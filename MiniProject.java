import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    Map<String, Integer> quoteSeeds() {
        Map<String, Integer> seeds = new LinkedHashMap<>();
        stocks().forEach(stock -> seeds.put(stock.name, stock.price));
        return seeds;
    }

    Map<String, KisQuoteTarget> kisQuoteTargets() {
        Map<String, KisQuoteTarget> targets = new LinkedHashMap<>();
        stocks().forEach(stock -> targets.put(stock.name, new KisQuoteTarget(stock.name, stock.code)));
        return targets;
    }

    void applyBrokerTick(BrokerTick tick) {
        brokerSource = "내장 모의 증권사 소켓 서버";
        lastBrokerTick = LocalDateTime.now();
        brokerTicks.incrementAndGet();
        updateStock(marketStocks.get(tick.symbol), tick);
        members.values().forEach(member -> updateStock(member.stocks.get(tick.symbol), tick));
    }

    void applyKisQuote(BrokerTick tick) {
        brokerSource = "한국투자증권 KIS REST API";
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
        addStock("005930", "삼성전자", 72000, 1000, 2000, 1.05);
        addStock("000660", "SK하이닉스", 213000, 700, 3500, 1.04);
        addStock("373220", "LG에너지솔루션", 352000, 400, -5000, 0.98);
        addStock("207940", "삼성바이오로직스", 835000, 120, 8000, 1.02);
        addStock("005380", "현대차", 246000, 500, 2500, 1.01);
        addStock("000270", "기아", 118000, 650, -1200, 0.99);
        addStock("068270", "셀트리온", 184000, 450, 1700, 1.03);
        addStock("005490", "POSCO홀딩스", 392000, 240, -4500, 0.97);
        addStock("035420", "NAVER", 184000, 500, 2500, 1.08);
        addStock("035720", "카카오", 52000, 900, -800, 0.91);
        addStock("012330", "현대모비스", 228000, 300, -4000, 0.96);
        addStock("006400", "삼성SDI", 401000, 250, 4500, 1.02);
        addStock("051910", "LG화학", 365000, 250, -6500, 0.97);
        addStock("105560", "KB금융", 82500, 1000, 900, 1.01);
        addStock("055550", "신한지주", 52300, 1000, 500, 1.01);
        addStock("086790", "하나금융지주", 61500, 900, 650, 1.02);
        addStock("028260", "삼성물산", 142000, 350, -1000, 0.99);
        addStock("066570", "LG전자", 98500, 700, 1200, 1.03);
        addStock("096770", "SK이노베이션", 121000, 450, -1500, 0.98);
        addStock("003670", "포스코퓨처엠", 268000, 220, 5200, 1.06);
        addStock("012450", "한화에어로스페이스", 235000, 260, 3800, 1.04);
        addStock("329180", "현대중공업", 136000, 320, 2100, 1.02);
        addStock("009540", "HD한국조선해양", 152000, 320, 1800, 1.03);
        addStock("009150", "삼성전기", 153000, 420, 900, 1.01);
        addStock("323410", "카카오뱅크", 24100, 1200, -300, 0.98);
        addStock("259960", "크래프톤", 287000, 210, 3500, 1.02);
        addStock("352820", "하이브", 203000, 240, -2000, 0.98);
        addStock("036570", "엔씨소프트", 186000, 210, -1700, 0.99);
        addStock("090430", "아모레퍼시픽", 142000, 360, 2200, 1.03);
        addStock("003490", "대한항공", 23800, 1400, 150, 1.01);
        addStock("051900", "LG생활건강", 356000, 180, -2500, 0.99);
        addStock("011170", "롯데케미칼", 100800, 500, 1000, 1.12);
        addStock("010950", "S-Oil", 69200, 800, -600, 0.99);
        addStock("015760", "한국전력", 21300, 1600, 250, 1.02);
        addStock("033780", "KT&G", 94200, 500, 500, 1.01);
        addStock("000810", "삼성화재", 368000, 220, 4000, 1.02);
        addStock("006800", "미래에셋증권", 8250, 2000, 110, 1.02);
        addStock("034020", "두산에너빌리티", 21800, 1500, 420, 1.04);
        addStock("086520", "에코프로", 104000, 300, -1800, 0.98);
        addStock("247540", "에코프로비엠", 184000, 300, -2200, 0.98);
    }

    private void addStock(String code, String name, int price, int quantity, int priceFluct, double nextFluct) {
        marketStocks.put(name, new Stock(code, name, price, quantity, priceFluct, nextFluct));
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

    private String enc(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String dec(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
