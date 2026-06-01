import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MiniProjectApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        MiniProject project = new MiniProject();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MiniHandler(project));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("KH 미니프로젝트 웹앱 실행 중: http://localhost:" + port);
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
                send(exchange, 200, "text/html; charset=utf-8", MiniPage.render());
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/state".equals(path)) {
                send(exchange, 200, "application/json; charset=utf-8", project.stateJson());
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
                    case "/api/item/buy" -> project.buyItem(body);
                    case "/api/item/use" -> project.useItem(body);
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
    private final Map<String, Item> itemStore = new LinkedHashMap<>();
    private final AtomicLong memberIds = new AtomicLong(1000);
    private final AtomicInteger postIds = new AtomicInteger(1);
    private final AtomicInteger commentIds = new AtomicInteger(1);
    private final Random random = new Random();
    private Member currentMember;

    MiniProject() {
        seedStocks();
        seedItems();
        register(Map.of("name", "테스트회원", "id", "test1", "pwd", "1234"));
        currentMember = null;
        writeSeedPost("test1", "안녕하세요", "반갑습니다. 자유게시판 테스트 글입니다.");
        writeSeedPost("test2", "오늘의 투자 메모", "다음날 진행을 누르면 주가가 변동됩니다.");
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
                "stocks", Json.array(stocks().stream().map(Stock::toJson).toList()),
                "shares", Json.array(currentMember == null ? List.of() : currentMember.shares.values().stream().map(Share::toJson).toList()),
                "items", Json.array(itemStore.values().stream().map(item -> item.toJson(currentMember == null ? 0 : currentMember.items.getOrDefault(item.code, 0))).toList()),
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
        return Json.obj("ok", true, "message", currentMember.day + "일차가 되었습니다. 주가가 변동되었습니다.");
    }

    String buyItem(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        Item item = itemStore.get(text(body, "code"));
        if (item == null) return Json.obj("ok", false, "error", "없는 아이템입니다.");
        if (currentMember.balance < item.price) return Json.obj("ok", false, "error", "잔액이 부족합니다.");
        currentMember.balance -= item.price;
        currentMember.items.merge(item.code, 1, Integer::sum);
        return Json.obj("ok", true, "message", item.name + "을 구매했습니다.");
    }

    String useItem(Map<String, String> body) {
        if (currentMember == null) return Json.obj("ok", false, "error", "로그인이 필요합니다.");
        Item item = itemStore.get(text(body, "code"));
        if (item == null || currentMember.items.getOrDefault(item.code, 0) <= 0) {
            return Json.obj("ok", false, "error", "보유한 아이템이 없습니다.");
        }
        currentMember.items.compute(item.code, (key, count) -> count == null || count <= 1 ? null : count - 1);
        if ("predict".equals(item.code)) {
            Stock stock = stocks().get(random.nextInt(stocks().size()));
            double hint = stock.nextFluct * (0.8 + random.nextDouble() * 0.4);
            return Json.obj("ok", true, "message", "소문에 따르면 " + stock.name + "는 내일 " + String.format(Locale.US, "%.2f", hint) + "배로 변동합니다.");
        }
        String[] luck = {"매우 나쁨", "나쁨", "보통", "좋음", "매우 좋음"};
        return Json.obj("ok", true, "message", "오늘의 운세는 " + luck[random.nextInt(luck.length)] + "입니다.");
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
        marketStocks.put("삼성전자", new Stock("삼성전자", 72000, 1000, 2000, 1.05));
        marketStocks.put("현대모비스", new Stock("현대모비스", 228000, 300, -4000, 0.96));
        marketStocks.put("롯데케미칼", new Stock("롯데케미칼", 100800, 500, 1000, 1.12));
        marketStocks.put("카카오", new Stock("카카오", 52000, 800, -800, 0.91));
        marketStocks.put("네이버", new Stock("네이버", 184000, 400, 2500, 1.08));
    }

    private void seedItems() {
        itemStore.put("luck", new Item("luck", "오늘의운세", 500, "무작위 운세를 확인합니다."));
        itemStore.put("predict", new Item("predict", "주식가격예측", 10000, "무작위 종목의 다음날 변동 힌트를 확인합니다."));
    }

    private void writeSeedPost(String author, String title, String content) {
        posts.add(new BoardPost(postIds.getAndIncrement(), author, title, content));
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
        return Json.obj("name", name, "price", price, "quantity", quantity, "priceFluct", priceFluct, "nextFluct", String.format(Locale.US, "%.2f", nextFluct));
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

    Share buy(int orderQuantity, int totalPrice) {
        quantity += orderQuantity;
        purchasePrice += totalPrice;
        return this;
    }

    String toJson() {
        int average = quantity == 0 ? 0 : purchasePrice / quantity;
        return Json.obj("stockName", stockName, "quantity", quantity, "purchasePrice", purchasePrice, "averagePrice", average);
    }
}

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
    final LocalDateTime time = LocalDateTime.now();
    final String stockName;
    final int quantity;
    final int price;
    final String type;

    TradeLog(long memberUid, String stockName, int quantity, int price, String type) {
        this.memberUid = memberUid;
        this.stockName = stockName;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
    }

    LocalDateTime time() {
        return time;
    }

    String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "stockName", stockName, "quantity", quantity, "price", price, "type", type);
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
                      <div class="label">GitHub _miniproject 내용만 웹으로 구현</div>
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
                      <section><h2>아이템 상점</h2><div class="cards" id="items"></div></section>
                      <section><h2>자유 게시판</h2><form id="postForm"><label>제목<input name="title"></label><label>내용<input name="content"></label><button>글 작성</button></form><div class="cards" id="posts"></div></section>
                    </aside>
                  </main>
                  <script>
                    let state = {};
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
                    function render() {
                      const m = state.member || {};
                      document.getElementById('memberInfo').innerHTML = state.loggedIn ? [
                        ['아이디', m.id], ['이름', m.name], ['현재 날짜', `${m.day}일`], ['보유 자산', won(m.balance)]
                      ].map(x => `<div class="metric"><div class="label">${x[0]}</div><div class="value">${x[1]}</div></div>`).join('') : '<div class="body">로그인해주세요.</div>';
                      document.getElementById('stocks').innerHTML = state.stocks.map(s => `<tr><td>${html(s.name)}</td><td>${won(s.price)}</td><td>${s.quantity}</td><td class="${s.priceFluct>=0?'up':'down'}">${won(s.priceFluct)}</td><td>${s.nextFluct}</td></tr>`).join('');
                      document.getElementById('stockSelect').innerHTML = state.stocks.map(s => `<option value="${html(s.name)}">${html(s.name)}</option>`).join('');
                      document.getElementById('shares').innerHTML = state.shares.map(s => `<tr><td>${html(s.stockName)}</td><td>${s.quantity}</td><td>${won(s.purchasePrice)}</td><td>${won(s.averagePrice)}</td></tr>`).join('') || '<tr><td colspan="4">보유 주식이 없습니다.</td></tr>';
                      document.getElementById('logs').innerHTML = state.logs.map(l => `<tr><td>${l.time}</td><td>${l.type}</td><td>${html(l.stockName)}</td><td>${l.quantity}</td><td>${won(l.price)}</td></tr>`).join('') || '<tr><td colspan="5">거래 기록이 없습니다.</td></tr>';
                      document.getElementById('items').innerHTML = state.items.map(i => `<div class="card"><strong>${html(i.name)}</strong><div class="label">${html(i.description)}</div><div>가격 ${won(i.price)} · 보유 ${i.owned}개</div><div class="row-actions"><button onclick="buyItem('${i.code}')">구매</button><button class="secondary" onclick="useItem('${i.code}')">사용</button></div></div>`).join('');
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
                    async function buyItem(code) { try { alert((await api('/api/item/buy', {code})).message); await refresh(); } catch(err) { alert(err.message); } }
                    async function useItem(code) { try { alert((await api('/api/item/use', {code})).message); await refresh(); } catch(err) { alert(err.message); } }
                    async function comment(postId) { const input = document.getElementById('comment-' + postId); if (!input.value.trim()) return; await api('/api/comment/write', {postId, content:input.value}); input.value=''; await refresh(); }
                    refresh();
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
