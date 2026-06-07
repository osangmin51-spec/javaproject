import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiniProjectApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        MiniProject project = new MiniProject();
        MockBrokerServer brokerServer = new MockBrokerServer(9090, project.quoteSeeds());
        brokerServer.start();
        BrokerFeedClient brokerClient = new BrokerFeedClient("127.0.0.1", 9090, project::applyBrokerTick);
        brokerClient.start();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MiniHandler(project));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("KH 미니프로젝트 웹앱 실행 중: http://localhost:" + port);
        System.out.println("모의 증권사 소켓 서버 실행 중: tcp://127.0.0.1:9090");
    }
}

class MiniHandler implements HttpHandler {
    private final MiniProject project;

    MiniHandler(MiniProject project) {
        this.project = project;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if ("GET".equals(exchange.getRequestMethod()) && "/".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", MiniDashboardPage.render());
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/state".equals(path)) {
                send(exchange, 200, "application/json; charset=utf-8", project.stateJson());
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/news".equals(path)) {
                send(exchange, 200, "application/json; charset=utf-8", project.newsJson(query(exchange), "stockName"));
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> body = Json.parseObject(readBody(exchange));
                String json = switch (path) {
                    case "/api/register" -> project.register(body);
                    case "/api/login" -> project.login(body);
                    case "/api/logout" -> project.logout();
                    case "/api/stock/buy" -> project.buyStock(body);
                    case "/api/stock/sell" -> project.sellStock(body);
                    case "/api/day/next" -> project.nextDay();
                    case "/api/board/write" -> project.writePost(body);
                    case "/api/board/delete" -> project.deletePost(body);
                    case "/api/comment/write" -> project.writeComment(body);
                    case "/api/comment/delete" -> project.deleteComment(body);
                    default -> Json.obj("ok", false, "error", "없는 API입니다.");
                };
                send(exchange, json.contains("\"ok\":false") ? 400 : 200, "application/json; charset=utf-8", json);
                return;
            }
            send(exchange, 404, "application/json; charset=utf-8", Json.obj("ok", false, "error", "경로를 찾을 수 없습니다."));
        } catch (Exception ex) {
            send(exchange, 500, "application/json; charset=utf-8", Json.obj("ok", false, "error", ex.getMessage()));
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return values;
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            if (equals < 0) continue;
            values.put(
                    URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8)
            );
        }
        return values;
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

class MiniProject {
    private final Map<Long, Member> members = new ConcurrentHashMap<>();
    private final Map<String, Stock> marketStocks = new ConcurrentHashMap<>();
    private final List<BoardPost> posts = new ArrayList<>();
    private final List<TradeLog> logs = new ArrayList<>();
    private final NaverNewsClient newsClient = new NaverNewsClient();
    private final Path databaseDir = Path.of("data");
    private final AtomicLong memberIds = new AtomicLong(1000);
    private final AtomicInteger postIds = new AtomicInteger(1);
    private final AtomicInteger commentIds = new AtomicInteger(1);
    private final Random random = new Random();
    private final AtomicLong brokerTicks = new AtomicLong();
    private volatile String brokerSource = "내장 모의 증권사 소켓 서버";
    private volatile LocalDateTime lastBrokerTick;
    private Member currentMember;

    MiniProject() {
        seedStocks();
        loadDatabase();
        if (members.isEmpty()) {
            register(Map.of("name", "테스트회원", "id", "test1", "pwd", "1234"));
        }
        currentMember = null;
        writeSeedPost("test1", "안녕하세요", "반갑습니다. 자유게시판 테스트 글입니다.");
        writeSeedPost("broker", "실시간 구독 안내", "모의 증권사 서버가 소켓으로 가격을 전송하고 웹 서버가 이를 구독합니다.");
    }

    String register(Map<String, String> body) {
        String name = text(body, "name");
        String id = text(body, "id");
        String pwd = text(body, "pwd");
        if (name.isBlank() || id.isBlank() || pwd.isBlank()) {
            return Json.obj("ok", false, "error", "이름, 아이디, 비밀번호를 입력하세요.");
        }
        if (findById(id) != null) {
            return Json.obj("ok", false, "error", "이미 존재하는 아이디입니다.");
        }
        Member member = new Member(memberIds.incrementAndGet(), name, id, pwd, copyStocks(marketStocks));
        members.put(member.uid, member);
        saveDatabase();
        return Json.obj("ok", true, "message", "회원가입이 완료되었습니다.");
    }

    String login(Map<String, String> body) {
        Member member = findById(text(body, "id"));
        if (member == null || !member.pwd.equals(text(body, "pwd"))) {
            return Json.obj("ok", false, "error", "없는 아이디이거나 비밀번호가 틀렸습니다.");
        }
        currentMember = member;
        return Json.obj("ok", true, "message", member.id + "님 환영합니다.");
    }

    String logout() {
        currentMember = null;
        return Json.obj("ok", true, "message", "로그아웃했습니다.");
    }

    String stateJson() {
        return Json.obj(
                "ok", true,
                "loggedIn", currentMember != null,
                "member", currentMember == null ? "{}" : currentMember.toJson(),
                "broker", brokerJson(),
                "portfolio", portfolioJson(),
                "stocks", Json.array(stocks().stream().map(Stock::toJson).toList()),
                "shares", Json.array(currentMember == null ? List.of() : currentMember.shares.values().stream().map(this::shareJson).toList()),
                "posts", Json.array(posts.stream().sorted(Comparator.comparing(BoardPost::id).reversed()).map(BoardPost::toJson).toList()),
                "logs", Json.array(logs.stream().filter(log -> currentMember != null && log.memberUid == currentMember.uid).sorted(Comparator.comparing(TradeLog::time).reversed()).map(TradeLog::toJson).toList())
        );
    }

    String buyStock(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        String stockName = text(body, "stockName");
        int quantity = number(body, "quantity");
        Stock stock = currentMember.stocks.get(stockName);
        if (stock == null || quantity <= 0 || stock.quantity < quantity) {
            return Json.obj("ok", false, "error", "잘못된 수량 주문입니다.");
        }
        int total = stock.price * quantity;
        if (currentMember.balance < total) {
            return Json.obj("ok", false, "error", "잔액이 부족합니다.");
        }
        currentMember.balance -= total;
        stock.quantity -= quantity;
        currentMember.shares.compute(stockName, (key, share) -> share == null
                ? new Share(stockName, quantity, stock.price)
                : share.buy(quantity, total));
        logs.add(new TradeLog(currentMember.uid, stockName, quantity, total, "구매"));
        saveDatabase();
        return Json.obj("ok", true, "message", stockName + " " + quantity + "주를 구매했습니다.");
    }

    String sellStock(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        String stockName = text(body, "stockName");
        int quantity = number(body, "quantity");
        Stock stock = currentMember.stocks.get(stockName);
        Share share = currentMember.shares.get(stockName);
        if (stock == null || share == null || quantity <= 0 || share.quantity < quantity) {
            return Json.obj("ok", false, "error", "보유 수량이 부족합니다.");
        }
        int total = stock.price * quantity;
        currentMember.balance += total;
        stock.quantity += quantity;
        share.quantity -= quantity;
        if (share.quantity == 0) currentMember.shares.remove(stockName);
        logs.add(new TradeLog(currentMember.uid, stockName, quantity, total, "판매"));
        saveDatabase();
        return Json.obj("ok", true, "message", stockName + " " + quantity + "주를 판매했습니다.");
    }

    String nextDay() {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        currentMember.day++;
        for (Stock stock : currentMember.stocks.values()) {
            int previous = stock.price;
            stock.price = Math.max(100, (int) (previous * stock.nextFluct));
            stock.priceFluct = stock.price - previous;
            stock.nextFluct = 0.8 + random.nextDouble() * 0.4;
        }
        saveDatabase();
        return Json.obj("ok", true, "message", currentMember.day + "일차가 되었습니다. 주가가 변동되었습니다.");
    }

    List<String> symbolNames() {
        return marketStocks.values().stream().map(stock -> stock.name).sorted().toList();
    }

    Map<String, Integer> quoteSeeds() {
        Map<String, Integer> seeds = new LinkedHashMap<>();
        stocks().forEach(stock -> seeds.put(stock.name, stock.price));
        return seeds;
    }

    void applyBrokerTick(BrokerTick tick) {
        brokerSource = "내장 모의 증권사 소켓 서버";
        lastBrokerTick = LocalDateTime.now();
        brokerTicks.incrementAndGet();
        updateStock(marketStocks.get(tick.symbol), tick);
        members.values().forEach(member -> updateStock(member.stocks.get(tick.symbol), tick));
    }

    String newsJson(Map<String, String> body, String key) {
        String stockName = text(body, key);
        Stock stock = marketStocks.get(stockName);
        if (stock == null) return Json.obj("ok", false, "error", "없는 종목입니다.");
        return newsClient.search(stock.name + " 주가 실적 전망", stock.name);
    }

    String writePost(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        String title = text(body, "title");
        String content = text(body, "content");
        if (title.isBlank() || content.isBlank()) return Json.obj("ok", false, "error", "제목과 내용을 입력하세요.");
        posts.add(new BoardPost(postIds.getAndIncrement(), currentMember.id, title, content));
        return Json.obj("ok", true, "message", "게시글을 작성했습니다.");
    }

    String deletePost(Map<String, String> body) {
        int id = number(body, "id");
        posts.removeIf(post -> post.id == id && currentMember != null && post.author.equals(currentMember.id));
        return Json.obj("ok", true, "message", "게시글 삭제를 처리했습니다.");
    }

    String writeComment(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        int postId = number(body, "postId");
        String content = text(body, "content");
        BoardPost post = posts.stream().filter(p -> p.id == postId).findFirst().orElse(null);
        if (post == null || content.isBlank()) return Json.obj("ok", false, "error", "댓글을 작성할 수 없습니다.");
        post.comments.add(new Comment(commentIds.getAndIncrement(), currentMember.id, content));
        return Json.obj("ok", true, "message", "댓글을 작성했습니다.");
    }

    String deleteComment(Map<String, String> body) {
        int postId = number(body, "postId");
        int commentId = number(body, "commentId");
        posts.stream().filter(post -> post.id == postId).findFirst()
                .ifPresent(post -> post.comments.removeIf(comment -> comment.id == commentId && currentMember != null && comment.author.equals(currentMember.id)));
        return Json.obj("ok", true, "message", "댓글 삭제를 처리했습니다.");
    }

    private void seedStocks() {
        addStock("삼성전자", 72000, 1000, 2000, 1.05);
        addStock("SK하이닉스", 213000, 700, 3500, 1.04);
        addStock("LG에너지솔루션", 352000, 400, -5000, 0.98);
        addStock("삼성바이오로직스", 835000, 120, 8000, 1.02);
        addStock("현대차", 246000, 500, 2500, 1.01);
        addStock("기아", 118000, 650, -1200, 0.99);
        addStock("셀트리온", 184000, 450, 1700, 1.03);
        addStock("POSCO홀딩스", 392000, 240, -4500, 0.97);
        addStock("NAVER", 184000, 500, 2500, 1.08);
        addStock("카카오", 52000, 900, -800, 0.91);
        addStock("현대모비스", 228000, 300, -4000, 0.96);
        addStock("삼성SDI", 401000, 250, 4500, 1.02);
        addStock("LG화학", 365000, 250, -6500, 0.97);
        addStock("KB금융", 82500, 1000, 900, 1.01);
        addStock("신한지주", 52300, 1000, 500, 1.01);
        addStock("하나금융지주", 61500, 900, 650, 1.02);
        addStock("삼성물산", 142000, 350, -1000, 0.99);
        addStock("LG전자", 98500, 700, 1200, 1.03);
        addStock("SK이노베이션", 121000, 450, -1500, 0.98);
        addStock("포스코퓨처엠", 268000, 220, 5200, 1.06);
        addStock("한화에어로스페이스", 235000, 260, 3800, 1.04);
        addStock("현대중공업", 136000, 320, 2100, 1.02);
        addStock("HD한국조선해양", 152000, 320, 1800, 1.03);
        addStock("삼성전기", 153000, 420, 900, 1.01);
        addStock("카카오뱅크", 24100, 1200, -300, 0.98);
        addStock("크래프톤", 287000, 210, 3500, 1.02);
        addStock("하이브", 203000, 240, -2000, 0.98);
        addStock("엔씨소프트", 186000, 210, -1700, 0.99);
        addStock("아모레퍼시픽", 142000, 360, 2200, 1.03);
        addStock("대한항공", 23800, 1400, 150, 1.01);
        addStock("LG생활건강", 356000, 180, -2500, 0.99);
        addStock("롯데케미칼", 100800, 500, 1000, 1.12);
        addStock("S-Oil", 69200, 800, -600, 0.99);
        addStock("한국전력", 21300, 1600, 250, 1.02);
        addStock("KT&G", 94200, 500, 500, 1.01);
        addStock("삼성화재", 368000, 220, 4000, 1.02);
        addStock("미래에셋증권", 8250, 2000, 110, 1.02);
        addStock("두산에너빌리티", 21800, 1500, 420, 1.04);
        addStock("에코프로", 104000, 300, -1800, 0.98);
        addStock("에코프로비엠", 184000, 300, -2200, 0.98);
    }

    private void addStock(String name, int price, int quantity, int priceFluct, double nextFluct) {
        marketStocks.put(name, new Stock(name, price, quantity, priceFluct, nextFluct));
    }

    private void writeSeedPost(String author, String title, String content) {
        posts.add(new BoardPost(postIds.getAndIncrement(), author, title, content));
    }

    private void updateStock(Stock stock, BrokerTick tick) {
        if (stock == null) return;
        stock.price = Math.max(100, tick.price);
        stock.priceFluct = tick.change;
        stock.nextFluct = 1.0 + tick.percent / 100.0;
    }

    private String brokerJson() {
        return Json.obj(
                "source", brokerSource,
                "protocol", "TCP SOCKET SUB ALL",
                "port", 9090,
                "ticks", brokerTicks.get(),
                "lastTick", lastBrokerTick == null ? "-" : lastBrokerTick.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }

    private String portfolioJson() {
        if (currentMember == null) {
            return Json.obj("cash", 0, "stockValue", 0, "totalAsset", 0, "purchase", 0, "profit", 0, "profitRate", "0.00");
        }
        int stockValue = currentMember.shares.values().stream().mapToInt(share -> currentPrice(share.stockName) * share.quantity).sum();
        int purchase = currentMember.shares.values().stream().mapToInt(share -> share.purchasePrice).sum();
        int profit = stockValue - purchase;
        double rate = purchase == 0 ? 0.0 : profit * 100.0 / purchase;
        return Json.obj(
                "cash", currentMember.balance,
                "stockValue", stockValue,
                "totalAsset", currentMember.balance + stockValue,
                "purchase", purchase,
                "profit", profit,
                "profitRate", String.format(Locale.US, "%.2f", rate)
        );
    }

    private String shareJson(Share share) {
        int currentPrice = currentPrice(share.stockName);
        int value = currentPrice * share.quantity;
        int profit = value - share.purchasePrice;
        double rate = share.purchasePrice == 0 ? 0.0 : profit * 100.0 / share.purchasePrice;
        return share.toJson(currentPrice, value, profit, rate);
    }

    private int currentPrice(String stockName) {
        Stock stock = currentMember == null ? marketStocks.get(stockName) : currentMember.stocks.get(stockName);
        return stock == null ? 0 : stock.price;
    }

    private synchronized void loadDatabase() {
        Path membersFile = databaseDir.resolve("members.tsv");
        Path sharesFile = databaseDir.resolve("shares.tsv");
        Path tradesFile = databaseDir.resolve("trades.tsv");
        try {
            if (Files.exists(membersFile)) {
                for (String line : Files.readAllLines(membersFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] cols = line.split("\t", -1);
                    if (cols.length < 6) continue;
                    long uid = Long.parseLong(cols[0]);
                    Member member = new Member(uid, dec(cols[1]), dec(cols[2]), dec(cols[3]), copyStocks(marketStocks));
                    member.balance = Integer.parseInt(cols[4]);
                    member.day = Integer.parseInt(cols[5]);
                    members.put(uid, member);
                    memberIds.set(Math.max(memberIds.get(), uid));
                }
            }
            if (Files.exists(sharesFile)) {
                for (String line : Files.readAllLines(sharesFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] cols = line.split("\t", -1);
                    if (cols.length < 4) continue;
                    Member member = members.get(Long.parseLong(cols[0]));
                    if (member == null) continue;
                    member.shares.put(dec(cols[1]), new Share(dec(cols[1]), Integer.parseInt(cols[2]), Integer.parseInt(cols[3]), true));
                }
            }
            if (Files.exists(tradesFile)) {
                for (String line : Files.readAllLines(tradesFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] cols = line.split("\t", -1);
                    if (cols.length < 6) continue;
                    logs.add(new TradeLog(Long.parseLong(cols[0]), dec(cols[1]), Integer.parseInt(cols[2]), Integer.parseInt(cols[3]), dec(cols[4]), LocalDateTime.parse(cols[5])));
                }
            }
        } catch (Exception ex) {
            System.err.println("DB 파일 로드 실패: " + ex.getMessage());
        }
    }

    private synchronized void saveDatabase() {
        try {
            Files.createDirectories(databaseDir);
            List<String> memberLines = new ArrayList<>();
            memberLines.add("#uid\tname\tid\tpwd\tbalance\tday");
            members.values().stream().sorted(Comparator.comparingLong(member -> member.uid)).forEach(member ->
                    memberLines.add(member.uid + "\t" + enc(member.name) + "\t" + enc(member.id) + "\t" + enc(member.pwd) + "\t" + member.balance + "\t" + member.day));
            Files.write(databaseDir.resolve("members.tsv"), memberLines, StandardCharsets.UTF_8);

            List<String> shareLines = new ArrayList<>();
            shareLines.add("#memberUid\tstockName\tquantity\tpurchasePrice");
            members.values().stream().sorted(Comparator.comparingLong(member -> member.uid)).forEach(member ->
                    member.shares.values().stream().sorted(Comparator.comparing(share -> share.stockName)).forEach(share ->
                            shareLines.add(member.uid + "\t" + enc(share.stockName) + "\t" + share.quantity + "\t" + share.purchasePrice)));
            Files.write(databaseDir.resolve("shares.tsv"), shareLines, StandardCharsets.UTF_8);

            List<String> tradeLines = new ArrayList<>();
            tradeLines.add("#memberUid\tstockName\tquantity\tprice\ttype\ttime");
            logs.stream().sorted(Comparator.comparing(TradeLog::time)).forEach(log ->
                    tradeLines.add(log.memberUid + "\t" + enc(log.stockName) + "\t" + log.quantity + "\t" + log.price + "\t" + enc(log.type) + "\t" + log.time));
            Files.write(databaseDir.resolve("trades.tsv"), tradeLines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("DB 파일 저장 실패: " + ex.getMessage());
        }
    }

    private String enc(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String dec(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private Member findById(String id) {
        return members.values().stream().filter(member -> member.id.equals(id)).findFirst().orElse(null);
    }

    private List<Stock> stocks() {
        if (currentMember == null) return marketStocks.values().stream().sorted(Comparator.comparing(stock -> stock.name)).toList();
        return currentMember.stocks.values().stream().sorted(Comparator.comparing(stock -> stock.name)).toList();
    }

    private Map<String, Stock> copyStocks(Map<String, Stock> source) {
        Map<String, Stock> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    private String text(Map<String, String> body, String key) {
        return body.getOrDefault(key, "").trim();
    }

    private int number(Map<String, String> body, String key) {
        try {
            return Integer.parseInt(text(body, key));
        } catch (Exception ex) {
            return 0;
        }
    }
}

class Member {
    final long uid;
    final String name;
    final String id;
    final String pwd;
    final Map<String, Share> shares = new ConcurrentHashMap<>();
    final Map<String, Stock> stocks;
    final Map<String, Integer> items = new ConcurrentHashMap<>();
    int day = 1;
    int balance = 1_000_000;

    Member(long uid, String name, String id, String pwd, Map<String, Stock> stocks) {
        this.uid = uid;
        this.name = name;
        this.id = id;
        this.pwd = pwd;
        this.stocks = stocks;
    }

    String toJson() {
        return Json.obj("uid", uid, "name", name, "id", id, "day", day, "balance", balance);
    }
}

class Stock {
    final String name;
    int price;
    int quantity;
    int priceFluct;
    double nextFluct;

    Stock(String name, int price, int quantity, int priceFluct, double nextFluct) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.priceFluct = priceFluct;
        this.nextFluct = nextFluct;
    }

    Stock copy() {
        return new Stock(name, price, quantity, priceFluct, nextFluct);
    }

    String toJson() {
        int previous = price - priceFluct;
        double rate = previous == 0 ? 0.0 : priceFluct * 100.0 / previous;
        return Json.obj("name", name, "price", price, "quantity", quantity, "priceFluct", priceFluct, "changeRate", String.format(Locale.US, "%.2f", rate), "nextFluct", String.format(Locale.US, "%.2f", nextFluct));
    }
}

class Share {
    final String stockName;
    int quantity;
    int purchasePrice;

    Share(String stockName, int quantity, int unitPrice) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = unitPrice * quantity;
    }

    Share(String stockName, int quantity, int purchasePrice, boolean alreadyTotal) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = alreadyTotal ? purchasePrice : purchasePrice * quantity;
    }

    Share buy(int orderQuantity, int totalPrice) {
        quantity += orderQuantity;
        purchasePrice += totalPrice;
        return this;
    }

    String toJson() {
        int average = quantity == 0 ? 0 : purchasePrice / quantity;
        return Json.obj("stockName", stockName, "quantity", quantity, "purchasePrice", purchasePrice, "averagePrice", average);
    }

    String toJson(int currentPrice, int value, int profit, double profitRate) {
        int average = quantity == 0 ? 0 : purchasePrice / quantity;
        return Json.obj(
                "stockName", stockName,
                "quantity", quantity,
                "purchasePrice", purchasePrice,
                "averagePrice", average,
                "currentPrice", currentPrice,
                "value", value,
                "profit", profit,
                "profitRate", String.format(Locale.US, "%.2f", profitRate)
        );
    }
}

class NaverNewsClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final String clientId = System.getenv("NAVER_CLIENT_ID");
    private final String clientSecret = System.getenv("NAVER_CLIENT_SECRET");

    String search(String query, String stockName) {
        if (blank(clientId) || blank(clientSecret)) {
            return Json.obj(
                    "ok", true,
                    "stockName", stockName,
                    "source", "네이버 검색 뉴스 API",
                    "configured", false,
                    "message", "NAVER_CLIENT_ID와 NAVER_CLIENT_SECRET 환경변수를 설정하면 실제 뉴스가 표시됩니다.",
                    "items", "[]"
            );
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create("https://openapi.naver.com/v1/search/news.json?query=" + encoded + "&display=5&sort=date");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Json.obj(
                        "ok", true,
                        "stockName", stockName,
                        "source", "네이버 검색 뉴스 API",
                        "configured", true,
                        "message", "뉴스 API 응답 오류: HTTP " + response.statusCode(),
                        "items", "[]"
                );
            }
            return Json.obj(
                    "ok", true,
                    "stockName", stockName,
                    "source", "네이버 검색 뉴스 API",
                    "configured", true,
                    "message", "최신 뉴스입니다.",
                    "items", Json.array(parseItems(response.body()).stream().map(NewsArticle::toJson).toList())
            );
        } catch (Exception ex) {
            return Json.obj(
                    "ok", true,
                    "stockName", stockName,
                    "source", "네이버 검색 뉴스 API",
                    "configured", true,
                    "message", "뉴스를 불러오지 못했습니다: " + ex.getMessage(),
                    "items", "[]"
            );
        }
    }

    private List<NewsArticle> parseItems(String json) {
        List<NewsArticle> articles = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{\\s*\"title\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"originallink\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"link\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"description\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"pubDate\"\\s*:\\s*\"(.*?)\"\\s*}", Pattern.DOTALL).matcher(json);
        while (matcher.find() && articles.size() < 5) {
            articles.add(new NewsArticle(clean(matcher.group(1)), clean(matcher.group(3)), clean(matcher.group(4)), clean(matcher.group(5))));
        }
        return articles;
    }

    private String clean(String text) {
        return text
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

class NewsArticle {
    final String title;
    final String link;
    final String description;
    final String pubDate;

    NewsArticle(String title, String link, String description, String pubDate) {
        this.title = title;
        this.link = link;
        this.description = description;
        this.pubDate = pubDate;
    }

    String toJson() {
        return Json.obj("title", title, "link", link, "description", description, "pubDate", pubDate);
    }
}

abstract class CompanyProfile {
    final String companyName;
    final String sector;

    CompanyProfile(String companyName, String sector) {
        this.companyName = companyName;
        this.sector = sector;
    }
}

interface NewsKeywordProfile {
    String keyword();
}

class SamsungElectronicsProfile extends CompanyProfile { SamsungElectronicsProfile() { super("삼성전자", "반도체"); } }
class SkHynixProfile extends CompanyProfile { SkHynixProfile() { super("SK하이닉스", "반도체"); } }
class LgEnergySolutionProfile extends CompanyProfile { LgEnergySolutionProfile() { super("LG에너지솔루션", "2차전지"); } }
class SamsungBiologicsProfile extends CompanyProfile { SamsungBiologicsProfile() { super("삼성바이오로직스", "바이오"); } }
class HyundaiMotorProfile extends CompanyProfile { HyundaiMotorProfile() { super("현대차", "자동차"); } }
class KiaProfile extends CompanyProfile { KiaProfile() { super("기아", "자동차"); } }
class CelltrionProfile extends CompanyProfile { CelltrionProfile() { super("셀트리온", "바이오"); } }
class PoscoHoldingsProfile extends CompanyProfile { PoscoHoldingsProfile() { super("POSCO홀딩스", "철강"); } }
class NaverProfile extends CompanyProfile { NaverProfile() { super("NAVER", "인터넷"); } }
class KakaoProfile extends CompanyProfile { KakaoProfile() { super("카카오", "인터넷"); } }
class HyundaiMobisProfile extends CompanyProfile { HyundaiMobisProfile() { super("현대모비스", "자동차부품"); } }
class SamsungSdiProfile extends CompanyProfile { SamsungSdiProfile() { super("삼성SDI", "2차전지"); } }
class LgChemProfile extends CompanyProfile { LgChemProfile() { super("LG화학", "화학"); } }
class KbFinancialProfile extends CompanyProfile { KbFinancialProfile() { super("KB금융", "금융"); } }
class ShinhanFinancialProfile extends CompanyProfile { ShinhanFinancialProfile() { super("신한지주", "금융"); } }
class HanaFinancialProfile extends CompanyProfile { HanaFinancialProfile() { super("하나금융지주", "금융"); } }
class SamsungCAndTProfile extends CompanyProfile { SamsungCAndTProfile() { super("삼성물산", "지주"); } }
class LgElectronicsProfile extends CompanyProfile { LgElectronicsProfile() { super("LG전자", "전자"); } }
class SkInnovationProfile extends CompanyProfile { SkInnovationProfile() { super("SK이노베이션", "에너지"); } }
class PoscoFutureMProfile extends CompanyProfile { PoscoFutureMProfile() { super("포스코퓨처엠", "2차전지소재"); } }
class HanwhaAerospaceProfile extends CompanyProfile { HanwhaAerospaceProfile() { super("한화에어로스페이스", "방산"); } }
class HyundaiHeavyProfile extends CompanyProfile { HyundaiHeavyProfile() { super("현대중공업", "조선"); } }
class HdKsoeProfile extends CompanyProfile { HdKsoeProfile() { super("HD한국조선해양", "조선"); } }
class SamsungElectroMechanicsProfile extends CompanyProfile { SamsungElectroMechanicsProfile() { super("삼성전기", "전자부품"); } }
class KakaoBankProfile extends CompanyProfile { KakaoBankProfile() { super("카카오뱅크", "금융"); } }
class KraftonProfile extends CompanyProfile { KraftonProfile() { super("크래프톤", "게임"); } }
class HybeProfile extends CompanyProfile { HybeProfile() { super("하이브", "엔터테인먼트"); } }
class NcsoftProfile extends CompanyProfile { NcsoftProfile() { super("엔씨소프트", "게임"); } }
class AmorePacificProfile extends CompanyProfile { AmorePacificProfile() { super("아모레퍼시픽", "화장품"); } }
class KoreanAirProfile extends CompanyProfile { KoreanAirProfile() { super("대한항공", "항공"); } }
class LgHouseholdProfile extends CompanyProfile { LgHouseholdProfile() { super("LG생활건강", "생활소비재"); } }
class LotteChemicalProfile extends CompanyProfile { LotteChemicalProfile() { super("롯데케미칼", "화학"); } }
class SOilProfile extends CompanyProfile { SOilProfile() { super("S-Oil", "정유"); } }
class KepcoProfile extends CompanyProfile { KepcoProfile() { super("한국전력", "전력"); } }
class KtngProfile extends CompanyProfile { KtngProfile() { super("KT&G", "소비재"); } }
class SamsungFireProfile extends CompanyProfile { SamsungFireProfile() { super("삼성화재", "보험"); } }
class MiraeAssetProfile extends CompanyProfile { MiraeAssetProfile() { super("미래에셋증권", "증권"); } }
class DoosanEnerbilityProfile extends CompanyProfile { DoosanEnerbilityProfile() { super("두산에너빌리티", "에너지설비"); } }
class EcoproProfile extends CompanyProfile { EcoproProfile() { super("에코프로", "2차전지소재"); } }
class EcoproBmProfile extends CompanyProfile { EcoproBmProfile() { super("에코프로비엠", "2차전지소재"); } }

class SamsungElectronicsNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성전자 주가 실적 전망"; } }
class SkHynixNewsKeyword implements NewsKeywordProfile { public String keyword() { return "SK하이닉스 주가 실적 전망"; } }
class LgEnergySolutionNewsKeyword implements NewsKeywordProfile { public String keyword() { return "LG에너지솔루션 주가 실적 전망"; } }
class SamsungBiologicsNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성바이오로직스 주가 실적 전망"; } }
class HyundaiMotorNewsKeyword implements NewsKeywordProfile { public String keyword() { return "현대차 주가 실적 전망"; } }
class KiaNewsKeyword implements NewsKeywordProfile { public String keyword() { return "기아 주가 실적 전망"; } }
class CelltrionNewsKeyword implements NewsKeywordProfile { public String keyword() { return "셀트리온 주가 실적 전망"; } }
class PoscoHoldingsNewsKeyword implements NewsKeywordProfile { public String keyword() { return "POSCO홀딩스 주가 실적 전망"; } }
class NaverNewsKeyword implements NewsKeywordProfile { public String keyword() { return "NAVER 주가 실적 전망"; } }
class KakaoNewsKeyword implements NewsKeywordProfile { public String keyword() { return "카카오 주가 실적 전망"; } }
class HyundaiMobisNewsKeyword implements NewsKeywordProfile { public String keyword() { return "현대모비스 주가 실적 전망"; } }
class SamsungSdiNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성SDI 주가 실적 전망"; } }
class LgChemNewsKeyword implements NewsKeywordProfile { public String keyword() { return "LG화학 주가 실적 전망"; } }
class KbFinancialNewsKeyword implements NewsKeywordProfile { public String keyword() { return "KB금융 주가 실적 전망"; } }
class ShinhanFinancialNewsKeyword implements NewsKeywordProfile { public String keyword() { return "신한지주 주가 실적 전망"; } }
class HanaFinancialNewsKeyword implements NewsKeywordProfile { public String keyword() { return "하나금융지주 주가 실적 전망"; } }
class SamsungCAndTNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성물산 주가 실적 전망"; } }
class LgElectronicsNewsKeyword implements NewsKeywordProfile { public String keyword() { return "LG전자 주가 실적 전망"; } }
class SkInnovationNewsKeyword implements NewsKeywordProfile { public String keyword() { return "SK이노베이션 주가 실적 전망"; } }
class PoscoFutureMNewsKeyword implements NewsKeywordProfile { public String keyword() { return "포스코퓨처엠 주가 실적 전망"; } }
class HanwhaAerospaceNewsKeyword implements NewsKeywordProfile { public String keyword() { return "한화에어로스페이스 주가 실적 전망"; } }
class HyundaiHeavyNewsKeyword implements NewsKeywordProfile { public String keyword() { return "현대중공업 주가 실적 전망"; } }
class HdKsoeNewsKeyword implements NewsKeywordProfile { public String keyword() { return "HD한국조선해양 주가 실적 전망"; } }
class SamsungElectroMechanicsNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성전기 주가 실적 전망"; } }
class KakaoBankNewsKeyword implements NewsKeywordProfile { public String keyword() { return "카카오뱅크 주가 실적 전망"; } }
class KraftonNewsKeyword implements NewsKeywordProfile { public String keyword() { return "크래프톤 주가 실적 전망"; } }
class HybeNewsKeyword implements NewsKeywordProfile { public String keyword() { return "하이브 주가 실적 전망"; } }
class NcsoftNewsKeyword implements NewsKeywordProfile { public String keyword() { return "엔씨소프트 주가 실적 전망"; } }
class AmorePacificNewsKeyword implements NewsKeywordProfile { public String keyword() { return "아모레퍼시픽 주가 실적 전망"; } }
class KoreanAirNewsKeyword implements NewsKeywordProfile { public String keyword() { return "대한항공 주가 실적 전망"; } }
class LgHouseholdNewsKeyword implements NewsKeywordProfile { public String keyword() { return "LG생활건강 주가 실적 전망"; } }
class LotteChemicalNewsKeyword implements NewsKeywordProfile { public String keyword() { return "롯데케미칼 주가 실적 전망"; } }
class SOilNewsKeyword implements NewsKeywordProfile { public String keyword() { return "S-Oil 주가 실적 전망"; } }
class KepcoNewsKeyword implements NewsKeywordProfile { public String keyword() { return "한국전력 주가 실적 전망"; } }
class KtngNewsKeyword implements NewsKeywordProfile { public String keyword() { return "KT&G 주가 실적 전망"; } }
class SamsungFireNewsKeyword implements NewsKeywordProfile { public String keyword() { return "삼성화재 주가 실적 전망"; } }
class MiraeAssetNewsKeyword implements NewsKeywordProfile { public String keyword() { return "미래에셋증권 주가 실적 전망"; } }
class DoosanEnerbilityNewsKeyword implements NewsKeywordProfile { public String keyword() { return "두산에너빌리티 주가 실적 전망"; } }
class EcoproNewsKeyword implements NewsKeywordProfile { public String keyword() { return "에코프로 주가 실적 전망"; } }
class EcoproBmNewsKeyword implements NewsKeywordProfile { public String keyword() { return "에코프로비엠 주가 실적 전망"; } }

class Item {
    final String code;
    final String name;
    final int price;
    final String description;

    Item(String code, String name, int price, String description) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.description = description;
    }

    String toJson(int owned) {
        return Json.obj("code", code, "name", name, "price", price, "description", description, "owned", owned);
    }
}

class BoardPost {
    final int id;
    final String author;
    String title;
    String content;
    int views;
    final LocalDateTime createdAt = LocalDateTime.now();
    final List<Comment> comments = new ArrayList<>();

    BoardPost(int id, String author, String title, String content) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.content = content;
    }

    int id() {
        return id;
    }

    String toJson() {
        return Json.obj("id", id, "author", author, "title", title, "content", content, "views", views,
                "createdAt", createdAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "comments", Json.array(comments.stream().map(Comment::toJson).toList()));
    }
}

class Comment {
    final int id;
    final String author;
    String content;
    final LocalDateTime createdAt = LocalDateTime.now();

    Comment(int id, String author, String content) {
        this.id = id;
        this.author = author;
        this.content = content;
    }

    String toJson() {
        return Json.obj("id", id, "author", author, "content", content, "createdAt", createdAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}

class TradeLog {
    final long memberUid;
    final LocalDateTime time;
    final String stockName;
    final int quantity;
    final int price;
    final String type;

    TradeLog(long memberUid, String stockName, int quantity, int price, String type) {
        this(memberUid, stockName, quantity, price, type, LocalDateTime.now());
    }

    TradeLog(long memberUid, String stockName, int quantity, int price, String type, LocalDateTime time) {
        this.memberUid = memberUid;
        this.stockName = stockName;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.time = time;
    }

    LocalDateTime time() {
        return time;
    }

    String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "stockName", stockName, "quantity", quantity, "price", price, "type", type);
    }
}

class BrokerTick {
    final String symbol;
    final int price;
    final int change;
    final double percent;

    BrokerTick(String symbol, int price, int change, double percent) {
        this.symbol = symbol;
        this.price = price;
        this.change = change;
        this.percent = percent;
    }
}

class BrokerQuote {
    final String symbol;
    int price;
    int change;
    double percent;

    BrokerQuote(String symbol, int price) {
        this.symbol = symbol;
        this.price = price;
    }

    BrokerTick move(Random random) {
        int previous = price;
        double drift = 0.992 + random.nextDouble() * 0.016;
        price = Math.max(100, (int) Math.round(price * drift));
        change = price - previous;
        percent = previous == 0 ? 0.0 : change * 100.0 / previous;
        return new BrokerTick(symbol, price, change, percent);
    }
}

class MockBrokerServer {
    private final int port;
    private final Map<String, BrokerQuote> quotes = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<BrokerClientSession> clients = new CopyOnWriteArraySet<>();
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Random random = new Random();
    private volatile boolean running;

    MockBrokerServer(int port, Map<String, Integer> seeds) {
        this.port = port;
        seeds.forEach((symbol, price) -> quotes.put(symbol, new BrokerQuote(symbol, price)));
    }

    void start() {
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "mock-broker-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Thread tickThread = new Thread(this::tickLoop, "mock-broker-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket socket = serverSocket.accept();
                BrokerClientSession session = new BrokerClientSession(socket, quotes.keySet(), clients);
                clients.add(session);
                clientPool.submit(session);
            }
        } catch (IOException ex) {
            System.err.println("모의 증권사 소켓 서버 시작 실패: " + ex.getMessage());
        }
    }

    private void tickLoop() {
        while (running) {
            try {
                Thread.sleep(1000);
                quotes.values().stream()
                        .map(quote -> quote.move(random))
                        .forEach(tick -> clients.forEach(client -> client.sendTick(tick)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
}

class BrokerClientSession implements Runnable {
    private final Socket socket;
    private final Collection<String> symbols;
    private final CopyOnWriteArraySet<BrokerClientSession> clients;
    private final CopyOnWriteArraySet<String> subscriptions = new CopyOnWriteArraySet<>();
    private PrintWriter writer;

    BrokerClientSession(Socket socket, Collection<String> symbols, CopyOnWriteArraySet<BrokerClientSession> clients) {
        this.socket = socket;
        this.symbols = symbols;
        this.clients = clients;
    }

    @Override
    public void run() {
        try (Socket autoClose = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(autoClose.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(autoClose.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer = out;
            out.println("HELLO|MOCK_BROKER|COMMANDS=SUB,UNSUB,LIST,QUIT");
            String line;
            while ((line = reader.readLine()) != null) {
                handle(line.trim().toUpperCase(Locale.ROOT), out);
            }
        } catch (IOException ignored) {
        } finally {
            clients.remove(this);
        }
    }

    void sendTick(BrokerTick tick) {
        PrintWriter out = writer;
        if (out == null) return;
        if (subscriptions.contains("ALL") || subscriptions.contains(tick.symbol.toUpperCase(Locale.ROOT))) {
            out.println("TICK|" + tick.symbol + "|" + tick.price + "|" + tick.change + "|" + String.format(Locale.US, "%.2f", tick.percent) + "|" + LocalDateTime.now());
        }
    }

    private void handle(String command, PrintWriter out) {
        command = command.replace("\uFEFF", "");
        if ("LIST".equals(command)) {
            out.println("SYMBOLS|" + String.join(",", symbols));
            return;
        }
        if (command.startsWith("SUB ")) {
            String target = command.substring(4).trim();
            subscriptions.add(target.isBlank() ? "ALL" : target);
            out.println("OK|SUB|" + target);
            return;
        }
        if (command.startsWith("UNSUB ")) {
            String target = command.substring(6).trim();
            subscriptions.remove(target);
            out.println("OK|UNSUB|" + target);
            return;
        }
        if ("QUIT".equals(command)) {
            out.println("BYE");
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }
        out.println("ERROR|UNKNOWN_COMMAND");
    }
}

class BrokerFeedClient {
    private final String host;
    private final int port;
    private final Consumer<BrokerTick> consumer;
    private volatile boolean running = true;

    BrokerFeedClient(String host, int port, Consumer<BrokerTick> consumer) {
        this.host = host;
        this.port = port;
        this.consumer = consumer;
    }

    void start() {
        Thread thread = new Thread(this::connectLoop, "broker-feed-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectLoop() {
        while (running) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer.println("SUB ALL");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("TICK|")) {
                        parseTick(line);
                    }
                }
            } catch (IOException ex) {
                sleep(1000);
            }
        }
    }

    private void parseTick(String line) {
        String[] cols = line.split("\\|");
        if (cols.length < 5) return;
        try {
            consumer.accept(new BrokerTick(cols[1], Integer.parseInt(cols[2]), Integer.parseInt(cols[3]), Double.parseDouble(cols[4])));
        } catch (NumberFormatException ignored) {
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}

class MiniPage {
    static String render() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>KH 미니프로젝트 모의주식</title>
                  <style>
                    :root { --ink:#17202a; --muted:#667085; --line:#d8dee8; --panel:#fff; --bg:#f4f6fa; --blue:#1f5fbf; --green:#0d8b57; --red:#c2413d; }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:Segoe UI, Arial, sans-serif; background:var(--bg); color:var(--ink); }
                    header { padding:22px 28px; background:#102033; color:#fff; display:flex; justify-content:space-between; gap:16px; align-items:center; }
                    h1 { margin:0; font-size:24px; }
                    main { max-width:1380px; margin:0 auto; padding:22px; display:grid; grid-template-columns:320px 1fr 380px; gap:16px; align-items:start; }
                    section { background:var(--panel); border:1px solid var(--line); border-radius:8px; overflow:hidden; }
                    h2 { margin:0; padding:14px 16px; font-size:16px; border-bottom:1px solid var(--line); background:#fbfcfe; }
                    .body { padding:16px; }
                    .stack { display:grid; gap:16px; }
                    form { display:grid; gap:10px; padding:16px; }
                    label { display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:700; text-transform:uppercase; }
                    input, select { border:1px solid var(--line); border-radius:6px; padding:10px; font-size:14px; width:100%; }
                    button { border:0; border-radius:6px; padding:10px 12px; background:var(--blue); color:#fff; font-weight:800; cursor:pointer; }
                    button.secondary { background:#425466; }
                    table { width:100%; border-collapse:collapse; font-size:14px; }
                    th, td { padding:11px 12px; border-bottom:1px solid #edf0f5; text-align:left; }
                    th { color:var(--muted); font-size:12px; }
                    .grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px; padding:16px; }
                    .metric { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; }
                    .label { color:var(--muted); font-size:12px; }
                    .value { margin-top:6px; font-size:20px; font-weight:800; }
                    .up { color:var(--green); } .down { color:var(--red); }
                    .message { padding:0 16px 16px; color:var(--muted); min-height:22px; }
                    .row-actions { display:flex; gap:8px; flex-wrap:wrap; }
                    .cards { display:grid; gap:10px; padding:16px; }
                    .card { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; display:grid; gap:8px; }
                    .comments { color:var(--muted); font-size:13px; display:grid; gap:4px; }
                    .comment-form { display:grid; grid-template-columns:1fr auto; gap:8px; }
                    @media (max-width: 1080px) { main { grid-template-columns:1fr; } header { align-items:flex-start; flex-direction:column; } }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>모의 주식 투자 프로그램</h1>
                      <div class="label">Java 기반 모의주식투자 웹앱</div>
                    </div>
                    <div class="row-actions"><button onclick="refresh()">새로고침</button><button class="secondary" onclick="logout()">로그아웃</button></div>
                  </header>
                  <main>
                    <aside class="stack">
                      <section><h2>로그인</h2><form id="loginForm"><label>아이디<input name="id" value="test1"></label><label>비밀번호<input name="pwd" type="password" value="1234"></label><button>로그인</button></form><div class="message" id="loginMessage"></div></section>
                      <section><h2>회원가입</h2><form id="registerForm"><label>이름<input name="name"></label><label>아이디<input name="id"></label><label>비밀번호<input name="pwd" type="password"></label><button>회원가입</button></form></section>
                      <section><h2>회원 정보</h2><div class="grid" id="memberInfo"></div></section>
                    </aside>
                    <div class="stack">
                      <section><h2>주식 현황</h2><table><thead><tr><th>종목</th><th>가격</th><th>수량</th><th>변동폭</th><th>다음 변동</th></tr></thead><tbody id="stocks"></tbody></table></section>
                      <section><h2>주식 매매</h2><form id="tradeForm"><label>종목<select name="stockName" id="stockSelect"></select></label><label>수량<input name="quantity" type="number" min="1" value="1"></label><div class="row-actions"><button name="side" value="buy">구매</button><button name="side" value="sell" class="secondary">판매</button></div></form><div class="message" id="tradeMessage"></div></section>
                      <section><h2>보유 주식</h2><table><thead><tr><th>종목</th><th>수량</th><th>매입가</th><th>평단</th></tr></thead><tbody id="shares"></tbody></table></section>
                      <section><h2>거래 기록</h2><table><thead><tr><th>시간</th><th>구분</th><th>종목</th><th>수량</th><th>금액</th></tr></thead><tbody id="logs"></tbody></table></section>
                    </div>
                    <aside class="stack">
                      <section><h2>날짜 진행</h2><div class="body"><button onclick="nextDay()">다음날로 넘어가기</button></div><div class="message" id="dayMessage"></div></section>
                      <section><h2>자유 게시판</h2><form id="postForm"><label>제목<input name="title"></label><label>내용<input name="content"></label><button>글 작성</button></form><div class="cards" id="posts"></div></section>
                    </aside>
                  </main>
                  <script>
                    let state = {};
                    let refreshing = false;
                    const won = n => Number(n || 0).toLocaleString('ko-KR') + '원';
                    const html = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                    async function api(path, body) {
                      const options = body ? {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)} : {};
                      const res = await fetch(path, options);
                      const data = await res.json();
                      if (!res.ok || data.ok === false) throw new Error(data.error || '요청 실패');
                      return data;
                    }
                    async function refresh() {
                      if (refreshing) return;
                      refreshing = true;
                      try {
                        state = await api('/api/state');
                        render();
                      } finally {
                        refreshing = false;
                      }
                    }
                    function render() {
                      const m = state.member || {};
                      document.getElementById('memberInfo').innerHTML = state.loggedIn ? [
                        ['아이디', m.id], ['이름', m.name], ['현재 날짜', `${m.day}일`], ['보유 자산', won(m.balance)]
                      ].map(x => `<div class="metric"><div class="label">${x[0]}</div><div class="value">${x[1]}</div></div>`).join('') : '<div class="body">로그인해주세요.</div>';
                      document.getElementById('stocks').innerHTML = state.stocks.map(s => `<tr><td>${html(s.name)}</td><td>${won(s.price)}</td><td>${s.quantity}</td><td class="${s.priceFluct>=0?'up':'down'}">${won(s.priceFluct)}</td><td>${s.nextFluct}</td></tr>`).join('');
                      document.getElementById('stockSelect').innerHTML = state.stocks.map(s => `<option value="${html(s.name)}">${html(s.name)}</option>`).join('');
                      document.getElementById('shares').innerHTML = state.shares.map(s => `<tr><td>${html(s.stockName)}</td><td>${s.quantity}</td><td>${won(s.purchasePrice)}</td><td>${won(s.averagePrice)}</td></tr>`).join('') || '<tr><td colspan="4">보유 주식이 없습니다.</td></tr>';
                      document.getElementById('logs').innerHTML = state.logs.map(l => `<tr><td>${l.time}</td><td>${l.type}</td><td>${html(l.stockName)}</td><td>${l.quantity}</td><td>${won(l.price)}</td></tr>`).join('') || '<tr><td colspan="5">거래 기록이 없습니다.</td></tr>';
                      document.getElementById('posts').innerHTML = state.posts.map(p => `<div class="card"><strong>${html(p.title)}</strong><div class="label">${html(p.author)} · ${p.createdAt} · 조회 ${p.views}</div><div>${html(p.content)}</div><div class="comments">${p.comments.map(c => `<div>${html(c.author)}: ${html(c.content)}</div>`).join('')}</div><div class="comment-form"><input id="comment-${p.id}" placeholder="댓글"><button onclick="comment(${p.id})">댓글</button></div></div>`).join('');
                    }
                    async function submitForm(form, path) {
                      const payload = Object.fromEntries(new FormData(form).entries());
                      return api(path, payload);
                    }
                    document.getElementById('loginForm').addEventListener('submit', async e => { e.preventDefault(); try { const r = await submitForm(e.target, '/api/login'); document.getElementById('loginMessage').textContent = r.message; await refresh(); } catch(err) { document.getElementById('loginMessage').textContent = err.message; } });
                    document.getElementById('registerForm').addEventListener('submit', async e => { e.preventDefault(); try { const r = await submitForm(e.target, '/api/register'); document.getElementById('loginMessage').textContent = r.message; e.target.reset(); await refresh(); } catch(err) { document.getElementById('loginMessage').textContent = err.message; } });
                    document.getElementById('tradeForm').addEventListener('submit', async e => { e.preventDefault(); const button = e.submitter; const payload = Object.fromEntries(new FormData(e.target).entries()); try { const r = await api(button.value === 'buy' ? '/api/stock/buy' : '/api/stock/sell', payload); document.getElementById('tradeMessage').textContent = r.message; await refresh(); } catch(err) { document.getElementById('tradeMessage').textContent = err.message; } });
                    document.getElementById('postForm').addEventListener('submit', async e => { e.preventDefault(); try { await submitForm(e.target, '/api/board/write'); e.target.reset(); await refresh(); } catch(err) { alert(err.message); } });
                    async function logout() { await api('/api/logout', {}); await refresh(); }
                    async function nextDay() { const r = await api('/api/day/next', {}); document.getElementById('dayMessage').textContent = r.message; await refresh(); }
                    async function comment(postId) { const input = document.getElementById('comment-' + postId); if (!input.value.trim()) return; await api('/api/comment/write', {postId, content:input.value}); input.value=''; await refresh(); }
                    refresh();
                  </script>
                </body>
                </html>
                """;
    }
}

class MiniDashboardPage {
    static String render() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>KH 미니프로젝트 투자 대시보드</title>
                  <style>
                    :root { --ink:#182230; --muted:#667085; --line:#d7deea; --panel:#fff; --soft:#f6f8fb; --blue:#1f5fbf; --green:#0b7f55; --red:#bd3d3a; --amber:#a15c00; }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:Segoe UI, Arial, sans-serif; color:var(--ink); background:#eef2f7; }
                    header { background:#111d2f; color:#fff; padding:18px 24px; display:flex; justify-content:space-between; align-items:center; gap:16px; position:sticky; top:0; z-index:2; }
                    h1 { margin:0; font-size:22px; letter-spacing:0; }
                    h2 { margin:0; padding:14px 16px; border-bottom:1px solid var(--line); font-size:16px; background:#fbfcfe; }
                    h3 { margin:0; font-size:15px; }
                    button { border:0; border-radius:6px; padding:10px 13px; background:var(--blue); color:white; font-weight:800; cursor:pointer; }
                    button.secondary { background:#405164; }
                    button.buy { background:var(--green); }
                    button.sell { background:var(--red); }
                    button.ghost { background:#e7edf6; color:#1f2937; }
                    input, select { width:100%; border:1px solid var(--line); border-radius:6px; padding:10px; font-size:14px; background:white; }
                    label { display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
                    table { width:100%; border-collapse:collapse; font-size:14px; }
                    th, td { padding:11px 12px; border-bottom:1px solid #edf1f7; text-align:left; vertical-align:middle; }
                    th { color:var(--muted); font-size:12px; }
                    main { max-width:1440px; margin:0 auto; padding:20px; display:grid; gap:16px; }
                    section, .panel { background:var(--panel); border:1px solid var(--line); border-radius:8px; overflow:hidden; }
                    .topbar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
                    .pill { border:1px solid rgba(255,255,255,.22); border-radius:999px; padding:7px 10px; color:#dbe4ef; font-size:13px; }
                    .hero { display:grid; grid-template-columns:1fr; gap:16px; align-items:stretch; max-width:920px; }
                    .auth { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
                    .auth form { display:grid; gap:10px; padding:16px; }
                    .summary { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:12px; }
                    .metric { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px; min-height:86px; }
                    .labelText { color:var(--muted); font-size:12px; font-weight:800; text-transform:uppercase; }
                    .value { margin-top:8px; font-size:24px; font-weight:850; }
                    .layout { display:grid; grid-template-columns:1fr; gap:16px; align-items:start; }
                    .workspace { display:grid; gap:16px; }
                    .tradeBox { display:grid; grid-template-columns:1fr 110px; gap:10px; padding:16px; align-items:end; }
                    .quantityRow { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
                    .actions { display:flex; gap:8px; flex-wrap:wrap; }
                    .message { color:var(--muted); padding:0 16px 14px; min-height:22px; }
                    .stockName { font-weight:850; }
                    .stockRow { cursor:pointer; }
                    .stockRow.active { background:#edf5ff; }
                    .up { color:var(--green); } .down { color:var(--red); }
                    .tabs { display:flex; gap:8px; padding:12px 12px 0; flex-wrap:wrap; }
                    .tab { background:#e7edf6; color:#1f2937; }
                    .tab.active { background:var(--blue); color:#fff; }
                    .tabPanel { display:none; }
                    .tabPanel.active { display:block; }
                    .cards { display:grid; gap:10px; padding:16px; }
                    .card { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; display:grid; gap:8px; }
                    .itemHead, .postHead { display:flex; justify-content:space-between; gap:10px; align-items:flex-start; }
                    .postForm { display:grid; grid-template-columns:1fr; gap:10px; padding:16px; border-bottom:1px solid var(--line); }
                    .commentForm { display:grid; grid-template-columns:1fr auto; gap:8px; }
                    .comments { color:var(--muted); font-size:13px; display:grid; gap:4px; }
                    .empty { color:var(--muted); padding:16px; }
                    .detailGrid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:10px; padding:16px; }
                    .newsList { display:grid; gap:10px; padding:0 16px 16px; }
                    .newsItem { border:1px solid var(--line); border-radius:8px; padding:12px; background:#fbfcfe; display:grid; gap:6px; }
                    .newsItem a { color:var(--blue); font-weight:850; text-decoration:none; }
                    .newsItem p { margin:0; color:#344054; line-height:1.45; }
                    .newsMeta { color:var(--muted); font-size:12px; }
                    @media (max-width: 1100px) { .hero, .layout { grid-template-columns:1fr; } .summary { grid-template-columns:repeat(2,1fr); } }
                    @media (max-width: 720px) { header { align-items:flex-start; flex-direction:column; } .auth, .summary, .tradeBox, .quantityRow, .detailGrid { grid-template-columns:1fr; } main { padding:12px; } }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>KH 미니프로젝트 모의주식</h1>
                      <div class="labelText">실시간 가격 구독과 포트폴리오 손익을 확인하는 Java 웹앱</div>
                    </div>
                    <div class="topbar">
                      <span class="pill" id="loginPill">로그인 필요</span>
                      <button onclick="refresh()">새로고침</button>
                      <button class="secondary" onclick="logout()">로그아웃</button>
                    </div>
                  </header>

                  <main>
                    <div class="hero" id="authArea">
                      <section>
                        <h2>로그인 / 회원가입</h2>
                        <div class="auth">
                          <form id="loginForm">
                            <label>아이디<input name="id" value="test1"></label>
                            <label>비밀번호<input name="pwd" type="password" value="1234"></label>
                            <button>로그인</button>
                          </form>
                          <form id="registerForm">
                            <label>이름<input name="name" placeholder="홍길동"></label>
                            <label>아이디<input name="id" placeholder="새 아이디"></label>
                            <label>비밀번호<input name="pwd" type="password"></label>
                            <button class="secondary">회원가입</button>
                          </form>
                        </div>
                        <div class="message" id="loginMessage"></div>
                      </section>
                    </div>

                    <div class="summary" id="summary"></div>

                    <div class="layout">
                      <div class="workspace">
                        <section>
                          <h2>매매</h2>
                          <form id="tradeForm" class="tradeBox">
                            <label>종목 선택<select name="stockName" id="stockSelect"></select></label>
                            <label>수량<input name="quantity" type="number" min="1" value="1"></label>
                            <div class="actions">
                              <button class="buy" name="side" value="buy">구매</button>
                              <button class="sell" name="side" value="sell">판매</button>
                              <button type="button" class="ghost" onclick="nextDay()">다음날</button>
                            </div>
                          </form>
                          <div class="message" id="tradeMessage"></div>
                          <table>
                            <thead><tr><th>종목</th><th>가격</th><th>시장수량</th><th>변동폭</th><th>변동률</th></tr></thead>
                            <tbody id="stocks"></tbody>
                          </table>
                        </section>

                        <section>
                          <h2>종목 상세 / 뉴스</h2>
                          <div class="detailGrid" id="stockDetail"></div>
                          <div class="newsList" id="newsList"></div>
                        </section>

                        <section>
                          <div class="tabs">
                            <button class="tab active" onclick="showTab('portfolio')">보유</button>
                            <button class="tab" onclick="showTab('logs')">기록</button>
                            <button class="tab" onclick="showTab('board')">게시판</button>
                          </div>
                          <div class="tabPanel active" id="panel-portfolio">
                            <table><thead><tr><th>종목</th><th>수량</th><th>평단가</th><th>현재가</th><th>평가금액</th><th>손익</th><th>수익률</th></tr></thead><tbody id="shares"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-logs">
                            <table><thead><tr><th>시간</th><th>구분</th><th>종목</th><th>수량</th><th>금액</th></tr></thead><tbody id="logs"></tbody></table>
                          </div>
                          <div class="tabPanel" id="panel-board">
                            <form id="postForm" class="postForm">
                              <label>제목<input name="title" placeholder="투자 메모 제목"></label>
                              <label>내용<input name="content" placeholder="오늘의 전략이나 느낀 점"></label>
                              <button>게시글 작성</button>
                            </form>
                            <div class="cards" id="posts"></div>
                          </div>
                        </section>

                        <div class="message" id="dayMessage"></div>
                      </div>
                    </div>
                  </main>

                  <script>
                    let state = {};
                    let selectedStockName = '';
                    let selectedNews = null;
                    const won = n => Number(n || 0).toLocaleString('ko-KR') + '원';
                    const html = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                    async function api(path, body) {
                      const options = body ? {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)} : {};
                      const res = await fetch(path, options);
                      const data = await res.json();
                      if (!res.ok || data.ok === false) throw new Error(data.error || '요청 실패');
                      return data;
                    }
                    async function refresh() {
                      state = await api('/api/state');
                      render();
                    }
                    function stockByName(name) {
                      return (state.stocks || []).find(stock => stock.name === name);
                    }
                    function portfolioValue() {
                      return (state.shares || []).reduce((sum, share) => {
                        const stock = stockByName(share.stockName);
                        return sum + (stock ? Number(stock.price) * Number(share.quantity) : 0);
                      }, 0);
                    }
                    function render() {
                      const logged = !!state.loggedIn;
                      const member = state.member || {};
                      const portfolio = state.portfolio || {};
                      document.getElementById('authArea').style.display = logged ? 'none' : 'grid';
                      document.getElementById('loginPill').textContent = logged ? `${member.id} · ${member.day}일차` : '로그인 필요';
                      document.getElementById('summary').innerHTML = [
                        ['보유 현금', logged ? won(portfolio.cash) : '-', ''],
                        ['주식 평가액', logged ? won(portfolio.stockValue) : '-', ''],
                        ['총 자산', logged ? won(portfolio.totalAsset) : '-', ''],
                        ['실시간 손익', logged ? won(portfolio.profit) : '-', Number(portfolio.profit || 0) >= 0 ? 'up' : 'down'],
                        ['수익률', logged ? `${portfolio.profitRate}%` : '-', Number(portfolio.profit || 0) >= 0 ? 'up' : 'down']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${value}</div></div>`).join('');
                      if (!selectedStockName && (state.stocks || []).length) selectedStockName = state.stocks[0].name;
                      renderStocks();
                      renderSelectedStock();
                      renderShares();
                      renderLogs();
                      renderPosts();
                    }
                    function renderStocks() {
                      document.getElementById('stocks').innerHTML = (state.stocks || []).map(stock => `<tr>
                        <td><button type="button" class="ghost" onclick="selectStock('${html(stock.name)}')">${html(stock.name)}</button></td>
                        <td>${won(stock.price)}</td>
                        <td>${stock.quantity}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${won(stock.priceFluct)}</td>
                        <td class="${stock.priceFluct>=0?'up':'down'}">${stock.changeRate}%</td>
                      </tr>`).join('');
                      document.getElementById('stockSelect').innerHTML = (state.stocks || []).map(stock => `<option value="${html(stock.name)}">${html(stock.name)} · ${won(stock.price)}</option>`).join('');
                      if (selectedStockName) document.getElementById('stockSelect').value = selectedStockName;
                    }
                    function renderSelectedStock() {
                      const stock = stockByName(selectedStockName) || (state.stocks || [])[0];
                      if (!stock) {
                        document.getElementById('stockDetail').innerHTML = '<div class="empty">종목이 없습니다.</div>';
                        document.getElementById('newsList').innerHTML = '';
                        return;
                      }
                      const positive = Number(stock.priceFluct || 0) >= 0;
                      document.getElementById('stockDetail').innerHTML = [
                        ['선택 종목', stock.name, ''],
                        ['현재가', won(stock.price), ''],
                        ['변동폭', won(stock.priceFluct), positive ? 'up' : 'down'],
                        ['변동률', `${stock.changeRate}%`, positive ? 'up' : 'down']
                      ].map(([label, value, cls]) => `<div class="metric"><div class="labelText">${label}</div><div class="value ${cls}">${html(value)}</div></div>`).join('');
                      renderNews();
                    }
                    function renderShares() {
                      document.getElementById('shares').innerHTML = (state.shares || []).map(share => {
                        const positive = Number(share.profit || 0) >= 0;
                        return `<tr><td>${html(share.stockName)}</td><td>${share.quantity}</td><td>${won(share.averagePrice)}</td><td>${won(share.currentPrice)}</td><td>${won(share.value)}</td><td class="${positive?'up':'down'}">${won(share.profit)}</td><td class="${positive?'up':'down'}">${share.profitRate}%</td></tr>`;
                      }).join('') || '<tr><td colspan="7" class="empty">보유 주식이 없습니다.</td></tr>';
                    }
                    function renderLogs() {
                      document.getElementById('logs').innerHTML = (state.logs || []).map(log => `<tr><td>${log.time}</td><td>${log.type}</td><td>${html(log.stockName)}</td><td>${log.quantity}</td><td>${won(log.price)}</td></tr>`).join('') || '<tr><td colspan="5" class="empty">거래 기록이 없습니다.</td></tr>';
                    }
                    function renderNews() {
                      const box = document.getElementById('newsList');
                      if (!selectedNews || selectedNews.stockName !== selectedStockName) {
                        box.innerHTML = '<div class="empty">종목을 클릭하면 관련 뉴스가 표시됩니다.</div>';
                        return;
                      }
                      const items = selectedNews.items || [];
                      const head = `<div class="message">${html(selectedNews.source)} · ${html(selectedNews.message)}</div>`;
                      box.innerHTML = head + (items.length ? items.map(item => `<article class="newsItem">
                        <a href="${html(item.link)}" target="_blank" rel="noopener noreferrer">${html(item.title)}</a>
                        <p>${html(item.description)}</p>
                        <div class="newsMeta">${html(item.pubDate)}</div>
                      </article>`).join('') : '<div class="empty">표시할 뉴스가 없습니다.</div>');
                    }
                    async function selectStock(name) {
                      selectedStockName = name;
                      selectedNews = null;
                      document.getElementById('stockSelect').value = name;
                      renderSelectedStock();
                      try {
                        selectedNews = await api('/api/news?stockName=' + encodeURIComponent(name));
                        renderNews();
                      } catch (err) {
                        selectedNews = {stockName:name, source:'뉴스', message:err.message, items:[]};
                        renderNews();
                      }
                    }
                    function renderPosts() {
                      document.getElementById('posts').innerHTML = (state.posts || []).map(post => `<div class="card">
                        <div class="postHead"><strong>${html(post.title)}</strong><span class="labelText">${html(post.author)} · ${post.createdAt}</span></div>
                        <div>${html(post.content)}</div>
                        <div class="comments">${(post.comments || []).map(comment => `<div>${html(comment.author)}: ${html(comment.content)}</div>`).join('')}</div>
                        <div class="commentForm"><input id="comment-${post.id}" placeholder="댓글 입력"><button onclick="comment(${post.id})">댓글</button></div>
                      </div>`).join('') || '<div class="empty">게시글이 없습니다.</div>';
                    }
                    function showTab(name) {
                      const labels = {portfolio:'보유', logs:'기록', board:'게시판'};
                      document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
                      document.querySelectorAll('.tabPanel').forEach(panel => panel.classList.remove('active'));
                      document.querySelectorAll('.tab').forEach(tab => { if (tab.textContent === labels[name]) tab.classList.add('active'); });
                      document.getElementById('panel-' + name).classList.add('active');
                    }
                    async function submitForm(form, path) {
                      return api(path, Object.fromEntries(new FormData(form).entries()));
                    }
                    document.getElementById('loginForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { const result = await submitForm(e.target, '/api/login'); document.getElementById('loginMessage').textContent = result.message; await refresh(); }
                      catch (err) { document.getElementById('loginMessage').textContent = err.message; }
                    });
                    document.getElementById('registerForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { const result = await submitForm(e.target, '/api/register'); document.getElementById('loginMessage').textContent = result.message; e.target.reset(); await refresh(); }
                      catch (err) { document.getElementById('loginMessage').textContent = err.message; }
                    });
                    document.getElementById('tradeForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      const payload = Object.fromEntries(new FormData(e.target).entries());
                      try { const result = await api(e.submitter.value === 'buy' ? '/api/stock/buy' : '/api/stock/sell', payload); document.getElementById('tradeMessage').textContent = result.message; await refresh(); }
                      catch (err) { document.getElementById('tradeMessage').textContent = err.message; }
                    });
                    document.getElementById('stockSelect').addEventListener('change', e => selectStock(e.target.value));
                    document.getElementById('postForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      try { await submitForm(e.target, '/api/board/write'); e.target.reset(); await refresh(); showTab('board'); }
                      catch (err) { alert(err.message); }
                    });
                    async function logout() { await api('/api/logout', {}); await refresh(); }
                    async function nextDay() { try { const result = await api('/api/day/next', {}); document.getElementById('dayMessage').textContent = result.message; await refresh(); } catch (err) { document.getElementById('dayMessage').textContent = err.message; } }
                    async function comment(postId) {
                      const input = document.getElementById('comment-' + postId);
                      if (!input.value.trim()) return;
                      await api('/api/comment/write', {postId, content:input.value});
                      input.value = '';
                      await refresh();
                      showTab('board');
                    }
                    refresh();
                    setInterval(refresh, 1000);
                  </script>
                </body>
                </html>
                """;
    }
}

class Json {
    static String obj(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) out.append(',');
            out.append(quote(String.valueOf(pairs[i]))).append(':').append(value(pairs[i + 1]));
        }
        return out.append('}').toString();
    }

    static String array(Collection<String> values) {
        return "[" + String.join(",", values) + "]";
    }

    static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof String text && (text.startsWith("{") || text.startsWith("["))) return text;
        return quote(String.valueOf(value));
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static Map<String, String> parseObject(String json) {
        Map<String, String> map = new HashMap<>();
        String body = json == null ? "" : json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        for (String part : body.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            map.put(unquote(part.substring(0, colon)), unquote(part.substring(colon + 1)));
        }
        return map;
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        return URLDecoder.decode(text.replace("\\\"", "\"").replace("\\n", "\n"), StandardCharsets.UTF_8);
    }
}
