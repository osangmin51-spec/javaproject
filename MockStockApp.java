import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockStockApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        AppContext app = new AppContext();
        HttpServer server = ServerFactory.create(port, app);
        server.start();
        System.out.println("모의주식투자 웹사이트 실행 중: http://localhost:" + port);
    }
}

class AppContext {
    final Clock clock = new SystemClock();
    final IdGenerator ids = new IdGenerator();
    final InMemoryDatabase database = new InMemoryDatabase();
    final EventBus eventBus = new SimpleEventBus();
    final AuditLogger auditLogger = new ConsoleAuditLogger(clock);
    final FeePolicy feePolicy = new FlatFeePolicy();
    final Sp500ImportService sp500ImportService = new Sp500ImportService(database);
    final FileStateStore stateStore = new FileStateStore(database);
    final MarketDataService marketDataService = new SimulatedMarketDataService(database, new RandomWalkPriceEngine(), eventBus);
    final UserRepository userRepository = new InMemoryUserRepository();
    final SessionStore sessionStore = new InMemorySessionStore();
    final AuthenticationService authenticationService = new DemoAuthenticationService(userRepository, sessionStore);
    final TransactionLedger ledger = new TransactionLedger();
    final RiskService riskService = new DefaultRiskService(database);
    final OrderValidator orderValidator = new OrderValidator(List.of(
            new SymbolValidator(database),
            new QuantityValidator(),
            new CashValidator(database, marketDataService, feePolicy),
            new HoldingValidator(database)
    ));
    final OrderExecutor marketOrderExecutor = new MarketOrderExecutor(database, marketDataService, feePolicy, ledger, eventBus, auditLogger);
    final OrderExecutor limitOrderExecutor = new LimitOrderExecutor(database, marketDataService, feePolicy, ledger, eventBus, auditLogger, orderValidator);
    final PendingOrderProcessor pendingOrderProcessor = new PendingOrderProcessor(database, limitOrderExecutor);
    final TradeService tradeService = new DefaultTradeService(orderValidator, marketOrderExecutor, limitOrderExecutor, ids, database);
    final WatchlistService watchlistService = new DefaultWatchlistService(database);
    final AccountService accountService = new DefaultAccountService(database, marketDataService);
    final AnalyticsService analyticsService = new AnalyticsService(database, marketDataService, stateStore);
    final LeaderboardService leaderboardService = new DefaultLeaderboardService(database, marketDataService);
    final ApiController apiController = new ApiController(this);
    final PageController pageController = new PageController(new HtmlRenderer());
    final HealthController healthController = new HealthController();
    final NotFoundController notFoundController = new NotFoundController();
    final ErrorHandler errorHandler = new ErrorHandler();

    AppContext() {
        seed();
        stateStore.load();
        ids.observe(database.orders.findAll());
        stateStore.save();
    }

    private void seed() {
        int imported = sp500ImportService.importNow();
        if (imported >= 100) {
            seedAccount();
            return;
        }
        seedFallbackStocks();
        seedAccount();
    }

    private void seedFallbackStocks() {
        database.stocks.save(new Stock("AAPL", "애플", "기술"));
        database.stocks.save(new Stock("MSFT", "마이크로소프트", "기술"));
        database.stocks.save(new Stock("NVDA", "엔비디아", "반도체"));
        database.stocks.save(new Stock("TSLA", "테슬라", "자동차"));
        database.stocks.save(new Stock("AMZN", "아마존", "소비재"));
        database.stocks.save(new Stock("GOOGL", "알파벳", "커뮤니케이션"));
        database.stocks.save(new Stock("JPM", "JP모건 체이스", "금융"));
        database.stocks.save(new Stock("KO", "코카콜라", "필수소비재"));
        database.quotes.save(new Quote("AAPL", Money.of(194.34), Money.of(191.20)));
        database.quotes.save(new Quote("MSFT", Money.of(428.12), Money.of(425.00)));
        database.quotes.save(new Quote("NVDA", Money.of(118.72), Money.of(116.55)));
        database.quotes.save(new Quote("TSLA", Money.of(177.92), Money.of(180.25)));
        database.quotes.save(new Quote("AMZN", Money.of(185.90), Money.of(183.40)));
        database.quotes.save(new Quote("GOOGL", Money.of(171.25), Money.of(170.88)));
        database.quotes.save(new Quote("JPM", Money.of(203.11), Money.of(201.66)));
        database.quotes.save(new Quote("KO", Money.of(62.44), Money.of(62.01)));
    }

    private void seedAccount() {
        Account account = new Account("demo", Money.of(100000.00), new Portfolio(), new Watchlist());
        database.accounts.save(account);
        userRepository.save(new User("demo", "Demo User"));
    }
}

class ServerFactory {
    static HttpServer create(int port, AppContext app) throws IOException {
        Router router = new Router(app.errorHandler, app.notFoundController);
        router.add(HttpMethod.GET, "/", app.pageController);
        router.add(HttpMethod.GET, "/health", app.healthController);
        router.add(HttpMethod.GET, "/api/quotes", app.apiController);
        router.add(HttpMethod.GET, "/api/account", app.apiController);
        router.add(HttpMethod.GET, "/api/analytics", app.apiController);
        router.add(HttpMethod.GET, "/api/leaderboard", app.apiController);
        router.add(HttpMethod.POST, "/api/orders", app.apiController);
        router.add(HttpMethod.POST, "/api/watchlist/add", app.apiController);
        router.add(HttpMethod.POST, "/api/watchlist/remove", app.apiController);
        router.add(HttpMethod.POST, "/api/sim/tick", app.apiController);
        router.add(HttpMethod.POST, "/api/import/sp500", app.apiController);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newCachedThreadPool());
        return server;
    }
}

class Router implements HttpHandler {
    private final Map<String, Controller> routes = new ConcurrentHashMap<>();
    private final ErrorHandler errorHandler;
    private final Controller fallback;

    Router(ErrorHandler errorHandler, Controller fallback) {
        this.errorHandler = errorHandler;
        this.fallback = fallback;
    }

    void add(String method, String path, Controller controller) {
        routes.put(method + " " + path, controller);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        RequestContext request = new RequestContext(exchange);
        ResponseWriter response = new ResponseWriter(exchange);
        try {
            Controller controller = routes.getOrDefault(request.method + " " + request.path, fallback);
            controller.handle(request, response);
        } catch (Exception ex) {
            errorHandler.write(ex, response);
        } finally {
            exchange.close();
        }
    }
}

class RequestContext {
    final HttpExchange exchange;
    final String method;
    final String path;
    final Map<String, String> query;

    RequestContext(HttpExchange exchange) {
        this.exchange = exchange;
        this.method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        this.path = exchange.getRequestURI().getPath();
        this.query = QueryParamParser.parse(exchange.getRequestURI().getRawQuery());
    }

    String body() throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    Map<String, String> jsonBody() throws IOException {
        return JsonUtil.parseObject(body());
    }
}

class ResponseWriter {
    private final HttpExchange exchange;

    ResponseWriter(HttpExchange exchange) {
        this.exchange = exchange;
    }

    void html(String html) throws IOException {
        send(200, ContentTypes.HTML, html);
    }

    void json(String json) throws IOException {
        send(200, ContentTypes.JSON, json);
    }

    void statusJson(int status, String json) throws IOException {
        send(status, ContentTypes.JSON, json);
    }

    void text(String text) throws IOException {
        send(200, ContentTypes.TEXT, text);
    }

    private void send(int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

class HtmlRenderer {
    String render() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>모의주식 투자 랩</title>
                  <style>
                    :root { color-scheme: light; --ink:#17202a; --muted:#6b7280; --line:#d8dee8; --panel:#ffffff; --green:#0d8b57; --red:#c2413d; --blue:#1f5fbf; --bg:#f5f7fb; }
                    * { box-sizing: border-box; }
                    body { margin:0; font-family: Inter, Segoe UI, Arial, sans-serif; background:var(--bg); color:var(--ink); }
                    header { background:#102033; color:white; padding:22px 28px; display:flex; align-items:center; justify-content:space-between; gap:20px; }
                    header h1 { margin:0; font-size:26px; font-weight:750; letter-spacing:0; }
                    header p { margin:6px 0 0; color:#cbd5e1; }
                    main { max-width:1480px; margin:0 auto; padding:24px; display:grid; grid-template-columns:330px minmax(0,1fr) 360px; gap:18px; align-items:start; }
                    section { background:var(--panel); border:1px solid var(--line); border-radius:8px; overflow:hidden; }
                    section h2 { margin:0; padding:16px 18px; font-size:17px; border-bottom:1px solid var(--line); background:#fbfcfe; }
                    table { width:100%; border-collapse:collapse; font-size:14px; }
                    th, td { padding:12px 14px; border-bottom:1px solid #edf0f5; text-align:left; white-space:nowrap; }
                    th { color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.04em; }
                    tr:hover td { background:#f8fafc; }
                    .grid { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; padding:16px; }
                    .metric { border:1px solid var(--line); border-radius:8px; padding:14px; background:#fbfcfe; min-height:78px; }
                    .label { color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.04em; }
                    .value { margin-top:8px; font-size:22px; font-weight:760; }
                    .up { color:var(--green); } .down { color:var(--red); }
                    form { display:grid; grid-template-columns:repeat(6, minmax(0,1fr)); gap:10px; padding:16px; align-items:end; }
                    label { display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:650; text-transform:uppercase; letter-spacing:.04em; }
                    input, select { width:100%; border:1px solid var(--line); border-radius:6px; padding:10px; font-size:14px; background:white; color:var(--ink); }
                    button { border:0; border-radius:6px; padding:11px 14px; background:var(--blue); color:white; font-weight:750; cursor:pointer; }
                    button.secondary { background:#425466; }
                    button.live-on { background:#0d8b57; }
                    button:hover { filter:brightness(.96); }
                    .span-2 { grid-column:span 2; }
                    .stack { display:grid; gap:18px; }
                    .body { padding:16px; }
                    .chips { display:flex; flex-wrap:wrap; gap:8px; }
                    .chip { display:inline-flex; align-items:center; gap:8px; border:1px solid var(--line); border-radius:999px; padding:8px 10px; background:#fbfcfe; }
                    .chip button { border-radius:999px; padding:3px 7px; background:#9aa4b2; }
                    .message { min-height:22px; padding:0 16px 16px; color:var(--muted); }
                    .toolbar { display:flex; gap:10px; flex-wrap:wrap; justify-content:flex-end; align-items:center; }
                    .live-status { color:#cbd5e1; font-size:13px; min-width:150px; text-align:right; }
                    .search-row { display:grid; grid-template-columns:1fr auto; gap:10px; padding:16px; border-bottom:1px solid var(--line); align-items:end; }
                    .search-results { display:flex; flex-wrap:wrap; gap:8px; padding:0 16px 14px; }
                    .search-results button { background:#e7edf6; color:#17202a; padding:8px 10px; }
                    .stock-detail { display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:12px; padding:16px; border-bottom:1px solid var(--line); }
                    .stock-detail .metric { min-height:90px; }
                    .estimate { display:grid; gap:6px; align-self:stretch; }
                    .estimate .value { font-size:18px; }
                    .pager { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 14px; border-top:1px solid var(--line); flex-wrap:wrap; }
                    .page-buttons { display:flex; gap:6px; flex-wrap:wrap; }
                    .page-buttons button { min-width:36px; padding:8px 10px; background:#e7edf6; color:#17202a; }
                    .page-buttons button.active { background:var(--blue); color:white; }
                    .rank { font-weight:760; color:#425466; }
                    .selectable-row { cursor:pointer; }
                    .selected-row td { background:#eef5ff !important; }
                    .trade-workspace section { min-width:0; }
                    .pnl-board { display:grid; grid-template-columns:1fr 1fr; gap:10px; padding:16px; }
                    .pnl-board .metric { min-height:88px; }
                    .market-list { max-height:650px; overflow:auto; }
                    .market-row td { white-space:normal; vertical-align:middle; }
                    .market-symbol { font-size:15px; font-weight:800; }
                    .market-name { margin-top:3px; color:var(--muted); font-size:12px; line-height:1.3; }
                    .stock-workbench { display:grid; gap:0; }
                    .detail-hero { padding:22px; display:grid; grid-template-columns:minmax(0,1.2fr) minmax(220px,.8fr); gap:18px; border-bottom:1px solid var(--line); background:linear-gradient(180deg,#ffffff,#f7f9fd); }
                    .hero-symbol { font-size:34px; font-weight:820; letter-spacing:0; }
                    .hero-name { color:var(--muted); margin-top:6px; line-height:1.4; }
                    .hero-price { font-size:38px; font-weight:820; text-align:right; }
                    .hero-change { margin-top:6px; text-align:right; font-weight:760; }
                    .detail-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; padding:16px; border-bottom:1px solid var(--line); }
                    .chart-panel { padding:16px; border-bottom:1px solid var(--line); }
                    .chart-head { display:flex; justify-content:space-between; gap:12px; align-items:center; margin-bottom:10px; }
                    .chart-head h3, .section-title { margin:0; font-size:15px; }
                    canvas { width:100%; height:190px; border:1px solid var(--line); border-radius:8px; background:#fbfcfe; }
                    .trade-form { grid-template-columns:1fr 1fr; }
                    .trade-form .full { grid-column:1 / -1; }
                    .side-tabs { display:grid; grid-template-columns:1fr 1fr; gap:8px; padding:16px 16px 0; }
                    .side-tabs button { background:#e7edf6; color:#17202a; }
                    .side-tabs button.active.buy { background:var(--green); color:white; }
                    .side-tabs button.active.sell { background:var(--red); color:white; }
                    .ticket-summary { display:grid; grid-template-columns:1fr 1fr; gap:10px; padding:0 16px 16px; }
                    .ticket-summary .metric { min-height:72px; }
                    .holding-list tbody tr { cursor:pointer; }
                    .empty { color:var(--muted); padding:16px; }
                    @media (max-width: 1180px) { main { grid-template-columns:1fr 1fr; } .stock-workbench { grid-column:1 / -1; grid-row:1; } }
                    @media (max-width: 920px) { main { grid-template-columns:1fr; padding:14px; } form, .trade-form { grid-template-columns:repeat(2, minmax(0,1fr)); } .grid, .stock-detail, .detail-grid, .pnl-board, .detail-hero { grid-template-columns:repeat(2,1fr); } .span-2 { grid-column:span 2; } header { align-items:flex-start; flex-direction:column; } .toolbar { justify-content:flex-start; } .live-status { text-align:left; } .search-row { grid-template-columns:1fr; } .hero-price, .hero-change { text-align:left; } }
                    @media (max-width: 640px) { .detail-hero, .detail-grid, .pnl-board, .ticket-summary { grid-template-columns:1fr; } form, .trade-form { grid-template-columns:1fr; } }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>모의주식 투자 랩</h1>
                      <p>실시간처럼 움직이는 가상 시세로 매수, 매도, 포트폴리오, 관심종목을 연습해보세요.</p>
                    </div>
                    <div class="toolbar">
                      <div class="live-status" id="liveStatus">실시간 꺼짐</div>
                      <button class="secondary" id="liveButton" onclick="toggleLive()">실시간 시작</button>
                      <button class="secondary" onclick="importSp500()">S&P 500 불러오기</button>
                      <button class="secondary" onclick="tickMarket()">시장 변동 1회</button>
                    </div>
                  </header>
                  <main class="trade-workspace">
                    <aside class="stack">
                      <section>
                        <h2>실시간 수익 현황</h2>
                        <div class="pnl-board" id="livePnl"></div>
                      </section>
                      <section>
                        <h2>종목 탐색</h2>
                        <div class="search-row">
                          <label>종목 검색 <input id="stockSearch" placeholder="AAPL 또는 Apple" oninput="searchStocks()"></label>
                          <button type="button" onclick="searchStocks()">검색</button>
                        </div>
                        <div class="search-results" id="searchResults"></div>
                        <div class="market-list">
                          <table>
                            <thead><tr><th>순위</th><th>종목</th><th>현재가</th><th>등락률</th></tr></thead>
                            <tbody id="quotes"></tbody>
                          </table>
                        </div>
                        <div class="pager">
                          <div class="label" id="quotePageSummary"></div>
                          <div class="page-buttons" id="quotePages"></div>
                        </div>
                      </section>
                    </aside>

                    <section class="stock-workbench">
                      <div class="detail-hero" id="selectedStockDetail"></div>
                      <div class="detail-grid" id="positionPanel"></div>
                      <div class="chart-panel">
                        <div class="chart-head">
                          <h3>선택 종목 실시간 추세</h3>
                          <div class="label" id="chartSummary">시세를 수집하는 중</div>
                        </div>
                        <canvas id="priceChart" width="760" height="220"></canvas>
                      </div>
                      <div class="body">
                        <h3 class="section-title">최근 주문</h3>
                        <table>
                          <thead><tr><th>구분</th><th>종목</th><th>수량</th><th>상태</th><th>체결가</th><th>시간</th></tr></thead>
                          <tbody id="orders"></tbody>
                        </table>
                      </div>
                    </section>

                    <aside class="stack">
                      <section>
                        <h2>매수 / 매도</h2>
                        <div class="side-tabs">
                          <button type="button" id="buyTab" class="active buy" onclick="setSide('BUY')">매수</button>
                          <button type="button" id="sellTab" class="sell" onclick="setSide('SELL')">매도</button>
                        </div>
                        <form id="orderForm" class="trade-form">
                          <input type="hidden" name="symbol" id="selectedSymbolInput">
                          <input type="hidden" name="side" id="orderSide" value="BUY">
                          <label class="full">선택 종목 <input id="selectedTradeSymbol" value="종목을 선택하세요" readonly></label>
                          <label>주문유형 <select name="type" id="orderType"><option value="MARKET">시장가</option><option value="LIMIT">지정가</option></select></label>
                          <label>수량 <input name="quantity" id="orderQuantity" type="number" min="1" value="1" required></label>
                          <label class="full">지정가 <input name="limitPrice" id="orderLimitPrice" type="number" step="0.01" placeholder="지정가 주문 때 입력"></label>
                          <button class="full" type="submit" id="submitOrderButton">매수 주문</button>
                        </form>
                        <div class="ticket-summary">
                          <div class="metric"><div class="label">예상 주문금액</div><div class="value" id="orderEstimate">$0.00</div></div>
                          <div class="metric"><div class="label">거래 후 예상</div><div class="value" id="afterTrade">-</div></div>
                        </div>
                        <div class="message" id="message"></div>
                      </section>
                      <section>
                        <h2>내 보유 종목</h2>
                        <table class="holding-list">
                          <thead><tr><th>종목</th><th>수량</th><th>평가</th><th>손익</th></tr></thead>
                          <tbody id="holdings"></tbody>
                        </table>
                      </section>
                      <section>
                        <h2>관심종목</h2>
                        <div class="body">
                          <div class="chips" id="watchlist"></div>
                        </div>
                      </section>
                    </aside>
                  </main>
                  <script>
                    const money = n => Number(n || 0).toLocaleString('ko-KR', {style:'currency', currency:'USD'});
                    const pct = n => `${Number(n || 0).toFixed(2)}%`;
                    const html = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                    const sideText = v => ({BUY:'매수', SELL:'매도'}[v] || v);
                    const statusText = v => ({NEW:'접수', PENDING:'대기', FILLED:'체결', REJECTED:'거절'}[v] || v);
                    const quotePageSize = 10;
                    const quotePageTotal = 10;
                    let quotePage = 1;
                    let allQuotes = [];
                    let topQuotes = [];
                    let selectedStock = null;
                    let selectedSide = 'BUY';
                    let lastAccount = null;
                    let lastAnalytics = null;
                    let liveTimer = null;
                    let liveBusy = false;
                    let lastTickAt = null;
                    const priceTrail = {};

                    async function api(path, options = {}) {
                      const res = await fetch(path, options);
                      const data = await res.json();
                      if (!res.ok || data.ok === false) throw new Error(data.error || '요청에 실패했습니다');
                      return data;
                    }

                    async function refresh() {
                      const [quotes, account, analytics] = await Promise.all([
                        api('/api/quotes'), api('/api/account'), api('/api/analytics')
                      ]);
                      allQuotes = quotes.items || [];
                      lastAccount = account;
                      lastAnalytics = analytics;
                      updatePriceTrails(allQuotes);
                      renderQuotes(allQuotes);
                      renderLivePnl();
                      renderSelectedStock();
                      renderPositionPanel();
                      renderTradeTicket();
                      renderHoldings();
                      renderOrders();
                      renderWatchlist();
                      renderSearchResults();
                      drawPriceChart();
                    }

                    function updatePriceTrails(items) {
                      items.slice(0, 180).forEach(q => {
                        const trail = priceTrail[q.symbol] || [];
                        trail.push(Number(q.price));
                        if (trail.length > 42) trail.shift();
                        priceTrail[q.symbol] = trail;
                      });
                    }

                    function getHolding(symbol = selectedStock?.symbol) {
                      return (lastAccount?.holdings || []).find(h => h.symbol === symbol);
                    }

                    function getAnalyticHolding(symbol = selectedStock?.symbol) {
                      return (lastAnalytics?.holdings || []).find(h => h.symbol === symbol);
                    }

                    function renderLivePnl() {
                      const holding = getAnalyticHolding();
                      const selectedGain = holding ? `${money(holding.gainLoss)} / ${pct(holding.gainLossPct)}` : '보유 없음';
                      document.getElementById('livePnl').innerHTML = [
                        ['총자산', money(lastAccount?.equity)],
                        ['총수익률', pct(lastAccount?.returnPct)],
                        ['예수금', money(lastAccount?.cash)],
                        ['보유 평가', money(lastAccount?.holdingsValue)],
                        ['선택 종목 손익', selectedGain],
                        ['대기 주문', lastAnalytics?.pendingOrders || 0]
                      ].map(([label, value]) => `<div class="metric"><div class="label">${label}</div><div class="value">${value}</div></div>`).join('');
                    }

                    function renderQuotes(items) {
                      topQuotes = items
                        .slice()
                        .sort((a, b) => Number(b.changePct) - Number(a.changePct))
                        .slice(0, quotePageSize * quotePageTotal);
                      if (selectedStock) selectedStock = allQuotes.find(q => q.symbol === selectedStock.symbol) || selectedStock;
                      if (!selectedStock && topQuotes.length > 0) selectedStock = topQuotes[0];
                      if (quotePage > quotePageTotal) quotePage = quotePageTotal;
                      renderQuotePage();
                    }

                    function renderQuotePage() {
                      const start = (quotePage - 1) * quotePageSize;
                      const pageItems = topQuotes.slice(start, start + quotePageSize);
                      document.getElementById('quotes').innerHTML = pageItems.map((q, i) => {
                        const rank = start + i + 1;
                        const prefix = Number(q.changePct) >= 0 ? '+' : '';
                        const selected = selectedStock && selectedStock.symbol === q.symbol ? 'selected-row' : '';
                        return `<tr class="market-row selectable-row ${selected}" onclick="selectStock('${q.symbol}')">
                          <td class="rank">${rank}</td>
                          <td><div class="market-symbol">${html(q.symbol)}</div><div class="market-name">${html(q.name)}</div></td>
                          <td>${money(q.price)}</td>
                          <td class="${q.changePct>=0?'up':'down'}">${prefix}${pct(q.changePct)}</td>
                        </tr>`;
                      }).join('');
                      document.getElementById('quotePageSummary').textContent = `${start + 1}-${start + pageItems.length}위 / 상위 100`;
                      document.getElementById('quotePages').innerHTML = Array.from({length: quotePageTotal}, (_, i) => {
                        const page = i + 1;
                        return `<button class="${page === quotePage ? 'active' : ''}" onclick="setQuotePage(${page})">${page}</button>`;
                      }).join('');
                    }

                    function setQuotePage(page) {
                      quotePage = page;
                      renderQuotePage();
                    }

                    function selectStock(symbol) {
                      selectedStock = allQuotes.find(q => q.symbol === symbol) || topQuotes.find(q => q.symbol === symbol);
                      if (!selectedStock) return;
                      document.getElementById('stockSearch').value = `${selectedStock.symbol} ${selectedStock.name}`;
                      renderQuotePage();
                      renderLivePnl();
                      renderSelectedStock();
                      renderPositionPanel();
                      renderTradeTicket();
                      renderSearchResults();
                      drawPriceChart();
                    }

                    function renderSelectedStock() {
                      const panel = document.getElementById('selectedStockDetail');
                      if (!selectedStock) {
                        panel.innerHTML = '<div><div class="hero-symbol">종목을 선택하세요</div><div class="hero-name">왼쪽 목록에서 종목을 누르면 상세 정보와 주문 티켓이 연결됩니다.</div></div>';
                        return;
                      }
                      const prefix = Number(selectedStock.changePct) >= 0 ? '+' : '';
                      const updated = lastTickAt ? lastTickAt.toLocaleTimeString('ko-KR') : '초기 시세';
                      panel.innerHTML = `
                        <div>
                          <div class="hero-symbol">${html(selectedStock.symbol)}</div>
                          <div class="hero-name">${html(selectedStock.name)} · ${html(selectedStock.sector)}</div>
                          <div style="margin-top:16px; display:flex; gap:8px; flex-wrap:wrap;">
                            <button type="button" onclick="addWatch('${selectedStock.symbol}')">관심 추가</button>
                            <button type="button" class="secondary" onclick="setSide('BUY')">바로 매수</button>
                            <button type="button" class="secondary" onclick="setSide('SELL')">바로 매도</button>
                          </div>
                        </div>
                        <div>
                          <div class="hero-price">${money(selectedStock.price)}</div>
                          <div class="hero-change ${selectedStock.changePct>=0?'up':'down'}">${prefix}${pct(selectedStock.changePct)} · ${money(selectedStock.change)}</div>
                          <div class="label" style="margin-top:14px; text-align:right;">마지막 갱신 ${updated}</div>
                        </div>`;
                    }

                    function renderPositionPanel() {
                      const holding = getHolding();
                      const analytic = getAnalyticHolding();
                      const quantity = holding ? holding.quantity : 0;
                      const marketValue = holding ? holding.marketValue : 0;
                      const avg = holding ? holding.averageCost : 0;
                      const gain = analytic ? `${money(analytic.gainLoss)} / ${pct(analytic.gainLossPct)}` : '보유 없음';
                      document.getElementById('positionPanel').innerHTML = [
                        ['보유수량', quantity],
                        ['평균단가', holding ? money(avg) : '-'],
                        ['평가금액', holding ? money(marketValue) : '-'],
                        ['실시간 손익', gain],
                        ['오늘 변동', selectedStock ? money(selectedStock.change) : '-'],
                        ['등락률', selectedStock ? pct(selectedStock.changePct) : '-'],
                        ['매수 가능 현금', money(lastAccount?.cash)],
                        ['체결 주문', lastAnalytics?.filledOrders || 0]
                      ].map(([label, value]) => `<div class="metric"><div class="label">${label}</div><div class="value">${value}</div></div>`).join('');
                    }

                    function searchStocks() {
                      renderSearchResults();
                    }

                    function renderSearchResults() {
                      const container = document.getElementById('searchResults');
                      const query = document.getElementById('stockSearch').value.trim().toLowerCase();
                      if (!query) {
                        container.innerHTML = '<span class="label">종목명이나 코드를 입력하거나 아래 목록에서 종목을 클릭하세요.</span>';
                        return;
                      }
                      const matches = allQuotes
                        .filter(q => q.symbol.toLowerCase().includes(query) || q.name.toLowerCase().includes(query))
                        .slice(0, 8);
                      container.innerHTML = matches.map(q => `<button type="button" onclick="selectStock('${q.symbol}')">${html(q.symbol)} · ${html(q.name)}</button>`).join('') || '<span class="label">검색 결과가 없습니다.</span>';
                    }

                    function setSide(side) {
                      selectedSide = side;
                      document.getElementById('orderSide').value = side;
                      document.getElementById('buyTab').classList.toggle('active', side === 'BUY');
                      document.getElementById('sellTab').classList.toggle('active', side === 'SELL');
                      document.getElementById('submitOrderButton').textContent = side === 'BUY' ? '매수 주문' : '매도 주문';
                      renderTradeTicket();
                    }

                    function renderTradeTicket() {
                      const symbolInput = document.getElementById('selectedSymbolInput');
                      const tradeSymbol = document.getElementById('selectedTradeSymbol');
                      if (!selectedStock) {
                        symbolInput.value = '';
                        tradeSymbol.value = '종목을 선택하세요';
                        renderOrderEstimate();
                        return;
                      }
                      symbolInput.value = selectedStock.symbol;
                      tradeSymbol.value = `${selectedStock.symbol} · ${selectedStock.name}`;
                      document.getElementById('orderLimitPrice').placeholder = `현재가 ${Number(selectedStock.price).toFixed(2)}`;
                      renderOrderEstimate();
                    }

                    function renderOrderEstimate() {
                      const estimate = document.getElementById('orderEstimate');
                      const after = document.getElementById('afterTrade');
                      if (!selectedStock) {
                        estimate.textContent = '$0.00';
                        after.textContent = '-';
                        return;
                      }
                      const quantity = Number(document.getElementById('orderQuantity').value || 0);
                      const type = document.getElementById('orderType').value;
                      const limit = Number(document.getElementById('orderLimitPrice').value || 0);
                      const price = type === 'LIMIT' && limit > 0 ? limit : Number(selectedStock.price);
                      const amount = quantity * price;
                      const holding = getHolding();
                      const currentQuantity = holding ? Number(holding.quantity) : 0;
                      const nextQuantity = selectedSide === 'BUY' ? currentQuantity + quantity : currentQuantity - quantity;
                      estimate.textContent = money(amount);
                      after.textContent = `${Math.max(nextQuantity, 0)}주`;
                    }

                    function renderHoldings() {
                      const rows = (lastAccount?.holdings || []).map(h => {
                        const analytic = getAnalyticHolding(h.symbol);
                        const gainPct = analytic ? ` / ${pct(analytic.gainLossPct)}` : '';
                        return `<tr onclick="selectStock('${h.symbol}')"><td>${html(h.symbol)}</td><td>${h.quantity}</td><td>${money(h.marketValue)}</td><td class="${h.gainLoss>=0?'up':'down'}">${money(h.gainLoss)}${gainPct}</td></tr>`;
                      }).join('');
                      document.getElementById('holdings').innerHTML = rows || '<tr><td colspan="4">아직 보유 종목이 없습니다.</td></tr>';
                    }

                    function renderOrders() {
                      const rows = (lastAccount?.orders || []).slice(0, 12).map(o => `<tr>
                        <td>${sideText(o.side)}</td><td>${html(o.symbol)}</td><td>${o.quantity}</td><td>${statusText(o.status)}</td><td>${o.fillPrice ? money(o.fillPrice) : '-'}</td><td>${o.createdAt}</td>
                      </tr>`).join('');
                      document.getElementById('orders').innerHTML = rows || '<tr><td colspan="6">아직 주문 내역이 없습니다.</td></tr>';
                    }

                    function renderWatchlist() {
                      const items = lastAccount?.watchlist || [];
                      document.getElementById('watchlist').innerHTML = items.map(s => `<span class="chip" onclick="selectStock('${s}')">${html(s)}<button aria-label="${s} 삭제" onclick="event.stopPropagation(); removeWatch('${s}')">x</button></span>`).join('') || '<span class="label">관심종목이 비어 있습니다.</span>';
                    }

                    function drawPriceChart() {
                      const canvas = document.getElementById('priceChart');
                      const ctx = canvas.getContext('2d');
                      ctx.clearRect(0, 0, canvas.width, canvas.height);
                      if (!selectedStock) return;
                      const data = priceTrail[selectedStock.symbol] || [];
                      document.getElementById('chartSummary').textContent = `${data.length}개 시세 포인트`;
                      if (data.length < 2) {
                        ctx.fillStyle = '#6b7280';
                        ctx.fillText('실시간 시세를 수집하는 중입니다.', 24, 36);
                        return;
                      }
                      const min = Math.min(...data);
                      const max = Math.max(...data);
                      const pad = 24;
                      const width = canvas.width - pad * 2;
                      const height = canvas.height - pad * 2;
                      ctx.strokeStyle = '#d8dee8';
                      ctx.beginPath();
                      ctx.moveTo(pad, canvas.height - pad);
                      ctx.lineTo(canvas.width - pad, canvas.height - pad);
                      ctx.stroke();
                      ctx.strokeStyle = data[data.length - 1] >= data[0] ? '#0d8b57' : '#c2413d';
                      ctx.lineWidth = 3;
                      ctx.beginPath();
                      data.forEach((price, index) => {
                        const x = pad + (index / (data.length - 1)) * width;
                        const y = pad + (1 - ((price - min) / Math.max(max - min, 0.01))) * height;
                        if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
                      });
                      ctx.stroke();
                      ctx.fillStyle = '#17202a';
                      ctx.fillText(`${money(data[data.length - 1])}`, pad, 18);
                    }

                    async function addWatch(symbol) {
                      await api('/api/watchlist/add', {method:'POST', body: JSON.stringify({symbol}), headers:{'Content-Type':'application/json'}});
                      await refresh();
                    }

                    async function removeWatch(symbol) {
                      await api('/api/watchlist/remove', {method:'POST', body: JSON.stringify({symbol}), headers:{'Content-Type':'application/json'}});
                      await refresh();
                    }

                    async function tickMarket() {
                      await runMarketTick(true);
                    }

                    async function runMarketTick(showMessage) {
                      if (liveBusy) return;
                      liveBusy = true;
                      try {
                        const result = await api('/api/sim/tick', {method:'POST'});
                        lastTickAt = new Date();
                        updateLiveStatus();
                        if (showMessage || result.autoFilled > 0) {
                          document.getElementById('message').textContent = result.autoFilled > 0
                            ? `시장 변동 후 대기 지정가 주문 ${result.autoFilled}건이 자동 체결되었습니다.`
                            : '시장 가격을 갱신했습니다. 자동 체결된 지정가 주문은 없습니다.';
                        }
                        await refresh();
                      } catch (err) {
                        document.getElementById('message').textContent = err.message;
                        stopLive();
                      } finally {
                        liveBusy = false;
                      }
                    }

                    function startLive() {
                      if (liveTimer) return;
                      liveTimer = setInterval(() => runMarketTick(false), 3000);
                      updateLiveStatus();
                    }

                    function toggleLive() {
                      if (liveTimer) stopLive(); else {
                        runMarketTick(false);
                        startLive();
                      }
                    }

                    function stopLive() {
                      if (liveTimer) clearInterval(liveTimer);
                      liveTimer = null;
                      updateLiveStatus();
                    }

                    function updateLiveStatus() {
                      const button = document.getElementById('liveButton');
                      const status = document.getElementById('liveStatus');
                      if (liveTimer) {
                        button.textContent = '실시간 중지';
                        button.classList.add('live-on');
                        status.textContent = lastTickAt ? `실시간 켜짐 · ${lastTickAt.toLocaleTimeString('ko-KR')}` : '실시간 켜짐';
                      } else {
                        button.textContent = '실시간 시작';
                        button.classList.remove('live-on');
                        status.textContent = lastTickAt ? `마지막 갱신 ${lastTickAt.toLocaleTimeString('ko-KR')}` : '실시간 꺼짐';
                      }
                    }

                    async function importSp500() {
                      try {
                        const result = await api('/api/import/sp500', {method:'POST'});
                        document.getElementById('message').textContent = `S&P 500 종목 ${result.count}개를 불러왔습니다.`;
                        await refresh();
                      } catch (err) {
                        document.getElementById('message').textContent = err.message;
                      }
                    }

                    document.getElementById('orderForm').addEventListener('submit', async e => {
                      e.preventDefault();
                      if (!selectedStock) {
                        document.getElementById('message').textContent = '먼저 주문할 종목을 선택해주세요.';
                        return;
                      }
                      const form = new FormData(e.target);
                      const payload = Object.fromEntries(form.entries());
                      payload.symbol = selectedStock.symbol;
                      payload.side = selectedSide;
                      try {
                        const result = await api('/api/orders', {method:'POST', body: JSON.stringify(payload), headers:{'Content-Type':'application/json'}});
                        document.getElementById('message').textContent = result.message;
                        await refresh();
                      } catch (err) {
                        document.getElementById('message').textContent = err.message;
                      }
                    });

                    ['orderType', 'orderQuantity', 'orderLimitPrice'].forEach(id => {
                      document.getElementById(id).addEventListener('input', renderOrderEstimate);
                      document.getElementById(id).addEventListener('change', renderOrderEstimate);
                    });

                    refresh().then(startLive);
                  </script>
                </body>
                </html>
                """;
    }
}

class JsonUtil {
    static String obj(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(String.valueOf(pairs[i]))).append(':').append(value(pairs[i + 1]));
        }
        return out.append('}').toString();
    }

    static String array(Collection<String> values) {
        return "[" + String.join(",", values) + "]";
    }

    static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof String text && (text.startsWith("{") || text.startsWith("["))) {
            return text;
        }
        if (value instanceof Money money) {
            return money.toJsonNumber();
        }
        if (value instanceof Percent percent) {
            return percent.toJsonNumber();
        }
        if (value instanceof JsonSerializable serializable) {
            return serializable.toJson();
        }
        return quote(String.valueOf(value));
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static Map<String, String> parseObject(String json) {
        Map<String, String> map = new HashMap<>();
        String body = json == null ? "" : json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String part : splitTopLevel(body)) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = unquote(part.substring(0, colon).trim());
            String value = unquote(part.substring(colon + 1).trim());
            map.put(key, value);
        }
        return map;
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        boolean quoted = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (c == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String unquote(String text) {
        String value = text.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }
}

class FormParser {
    static Map<String, String> parse(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}

class IdGenerator {
    private final AtomicLong orderIds = new AtomicLong(1000);

    String nextOrderId() {
        return "ORD-" + orderIds.incrementAndGet();
    }

    void observe(Collection<Order> orders) {
        for (Order order : orders) {
            String id = order.id().replace("ORD-", "");
            long value = NumberFormatUtil.parseLong(id, 0);
            orderIds.updateAndGet(current -> Math.max(current, value));
        }
    }
}

interface Clock {
    Instant now();

    default String displayNow() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(now());
    }
}

class SystemClock implements Clock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}

class InMemoryDatabase {
    final InMemoryRepository<Stock, String> stocks = new InMemoryRepository<>(Stock::symbol);
    final InMemoryRepository<Quote, String> quotes = new InMemoryRepository<>(Quote::symbol);
    final InMemoryRepository<Account, String> accounts = new InMemoryRepository<>(Account::id);
    final InMemoryRepository<Order, String> orders = new InMemoryRepository<>(Order::id);

    Account demoAccount() {
        return accounts.findById("demo").orElseThrow(() -> new AppException("데모 계좌를 찾을 수 없습니다"));
    }
}

class Account implements JsonSerializable {
    private final String id;
    private Money cash;
    final Portfolio portfolio;
    final Watchlist watchlist;

    Account(String id, Money cash, Portfolio portfolio, Watchlist watchlist) {
        this.id = id;
        this.cash = cash;
        this.portfolio = portfolio;
        this.watchlist = watchlist;
    }

    String id() {
        return id;
    }

    Money cash() {
        return cash;
    }

    void debit(Money amount) {
        cash = cash.minus(amount);
    }

    void credit(Money amount) {
        cash = cash.plus(amount);
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("id", id, "cash", cash);
    }
}

class Portfolio {
    private final Map<String, Holding> holdings = new ConcurrentHashMap<>();

    Collection<Holding> all() {
        return Sorter.bySymbol(holdings.values());
    }

    Holding holding(String symbol) {
        return holdings.get(symbol.toUpperCase(Locale.ROOT));
    }

    int quantity(String symbol) {
        Holding holding = holding(symbol);
        return holding == null ? 0 : holding.quantity();
    }

    void buy(String symbol, int quantity, Money price) {
        holdings.compute(symbol.toUpperCase(Locale.ROOT), (s, existing) -> existing == null
                ? new Holding(s, quantity, price)
                : existing.add(quantity, price));
    }

    void sell(String symbol, int quantity) {
        String key = symbol.toUpperCase(Locale.ROOT);
        Holding existing = holdings.get(key);
        if (existing == null || existing.quantity() < quantity) {
            throw new NotEnoughSharesException(key + " 매도 가능 수량이 부족합니다");
        }
        Holding changed = existing.remove(quantity);
        if (changed.quantity() == 0) {
            holdings.remove(key);
        } else {
            holdings.put(key, changed);
        }
    }
}

class Holding implements JsonSerializable {
    private final String symbol;
    private final int quantity;
    private final Money averageCost;

    Holding(String symbol, int quantity, Money averageCost) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    String symbol() {
        return symbol;
    }

    int quantity() {
        return quantity;
    }

    Money averageCost() {
        return averageCost;
    }

    Holding add(int addedQuantity, Money price) {
        Money currentCost = averageCost.times(quantity);
        Money addedCost = price.times(addedQuantity);
        int newQuantity = quantity + addedQuantity;
        return new Holding(symbol, newQuantity, currentCost.plus(addedCost).divide(newQuantity));
    }

    Holding remove(int removedQuantity) {
        return new Holding(symbol, quantity - removedQuantity, averageCost);
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("symbol", symbol, "quantity", quantity, "averageCost", averageCost);
    }
}

class Stock implements JsonSerializable {
    private final String symbol;
    private final String name;
    private final String sector;

    Stock(String symbol, String name, String sector) {
        this.symbol = symbol.toUpperCase(Locale.ROOT);
        this.name = name;
        this.sector = sector;
    }

    String symbol() {
        return symbol;
    }

    String name() {
        return name;
    }

    String sector() {
        return sector;
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("symbol", symbol, "name", name, "sector", sector);
    }
}

class Quote implements JsonSerializable {
    private final String symbol;
    private final Money price;
    private final Money previousClose;

    Quote(String symbol, Money price, Money previousClose) {
        this.symbol = symbol.toUpperCase(Locale.ROOT);
        this.price = price;
        this.previousClose = previousClose;
    }

    String symbol() {
        return symbol;
    }

    Money price() {
        return price;
    }

    Money previousClose() {
        return previousClose;
    }

    Money change() {
        return price.minus(previousClose);
    }

    Percent changePct() {
        return Percent.fromRatio(change().asDecimal().divide(previousClose.asDecimal(), 8, RoundingMode.HALF_UP));
    }

    Quote withPrice(Money nextPrice) {
        return new Quote(symbol, nextPrice, previousClose);
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("symbol", symbol, "price", price, "previousClose", previousClose, "change", change(), "changePct", changePct());
    }
}

class Market {
    boolean isOpen() {
        return true;
    }
}

interface MarketDataService {
    List<Quote> quotes();

    Quote quote(String symbol);

    void tick();
}

class SimulatedMarketDataService implements MarketDataService {
    private final InMemoryDatabase database;
    private final PriceEngine priceEngine;
    private final EventBus eventBus;

    SimulatedMarketDataService(InMemoryDatabase database, PriceEngine priceEngine, EventBus eventBus) {
        this.database = database;
        this.priceEngine = priceEngine;
        this.eventBus = eventBus;
    }

    @Override
    public List<Quote> quotes() {
        return Sorter.bySymbol(database.quotes.findAll());
    }

    @Override
    public Quote quote(String symbol) {
        return database.quotes.findById(symbol.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new SymbolNotFoundException("알 수 없는 종목코드입니다: " + symbol));
    }

    @Override
    public void tick() {
        for (Quote quote : quotes()) {
            Quote changed = quote.withPrice(priceEngine.next(quote));
            database.quotes.save(changed);
            eventBus.publish(new PriceChangedEvent(changed.symbol(), changed.price()));
        }
    }
}

interface PriceEngine {
    Money next(Quote quote);
}

class RandomWalkPriceEngine implements PriceEngine {
    private final Random random = new Random();

    @Override
    public Money next(Quote quote) {
        double drift = (random.nextDouble() - 0.48) * 0.025;
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(drift));
        BigDecimal next = quote.price().asDecimal().multiply(factor);
        if (next.compareTo(BigDecimal.ONE) < 0) {
            next = BigDecimal.ONE;
        }
        return Money.of(next);
    }
}

interface TradeService {
    Order place(Map<String, String> request);
}

class DefaultTradeService implements TradeService {
    private final OrderValidator validator;
    private final OrderExecutor marketExecutor;
    private final OrderExecutor limitExecutor;
    private final IdGenerator ids;
    private final InMemoryDatabase database;

    DefaultTradeService(OrderValidator validator, OrderExecutor marketExecutor, OrderExecutor limitExecutor, IdGenerator ids, InMemoryDatabase database) {
        this.validator = validator;
        this.marketExecutor = marketExecutor;
        this.limitExecutor = limitExecutor;
        this.ids = ids;
        this.database = database;
    }

    @Override
    public Order place(Map<String, String> request) {
        Order order = Order.from(ids.nextOrderId(), request);
        validator.validate(order);
        OrderExecutor executor = OrderType.LIMIT.equals(order.type()) ? limitExecutor : marketExecutor;
        Order executed = executor.execute(order);
        database.orders.save(executed);
        return executed;
    }
}

class Order implements JsonSerializable {
    private final String id;
    private final String symbol;
    private final String side;
    private final String type;
    private final int quantity;
    private final Money limitPrice;
    private String status = OrderStatus.NEW;
    private Money fillPrice;
    private Money fee = Money.zero();
    private String message = "주문이 접수되었습니다";
    private final Instant createdAt = Instant.now();

    Order(String id, String symbol, String side, String type, int quantity, Money limitPrice) {
        this.id = id;
        this.symbol = symbol.toUpperCase(Locale.ROOT);
        this.side = side.toUpperCase(Locale.ROOT);
        this.type = type.toUpperCase(Locale.ROOT);
        this.quantity = quantity;
        this.limitPrice = limitPrice;
    }

    static Order from(String id, Map<String, String> request) {
        String symbol = request.getOrDefault("symbol", "").trim();
        String side = request.getOrDefault("side", OrderSide.BUY).trim();
        String type = request.getOrDefault("type", OrderType.MARKET).trim();
        int quantity = NumberFormatUtil.parseInt(request.get("quantity"), 0);
        Money limitPrice = request.getOrDefault("limitPrice", "").isBlank() ? null : Money.of(NumberFormatUtil.parseDecimal(request.get("limitPrice"), BigDecimal.ZERO));
        return new Order(id, symbol, side, type, quantity, limitPrice);
    }

    static Order restore(String id, String symbol, String side, String type, int quantity, Money limitPrice, String status, Money fillPrice, Money fee, String message) {
        Order order = new Order(id, symbol, side, type, quantity, limitPrice);
        order.status = status;
        order.fillPrice = fillPrice;
        order.fee = fee == null ? Money.zero() : fee;
        order.message = message == null || message.isBlank() ? order.message : message;
        return order;
    }

    String id() {
        return id;
    }

    String symbol() {
        return symbol;
    }

    String side() {
        return side;
    }

    String type() {
        return type;
    }

    int quantity() {
        return quantity;
    }

    Money limitPrice() {
        return limitPrice;
    }

    Money fillPrice() {
        return fillPrice;
    }

    Money fee() {
        return fee;
    }

    String status() {
        return status;
    }

    String message() {
        return message;
    }

    void fill(Money price, Money fee) {
        this.status = OrderStatus.FILLED;
        this.fillPrice = price;
        this.fee = fee;
        this.message = sideText() + " " + symbol + " " + quantity + "주가 " + price.format() + "에 체결되었습니다";
    }

    void pend(String message) {
        this.status = OrderStatus.PENDING;
        this.message = message;
    }

    void reject(String message) {
        this.status = OrderStatus.REJECTED;
        this.message = message;
    }

    private String sideText() {
        return OrderSide.BUY.equals(side) ? "매수" : "매도";
    }

    @Override
    public String toJson() {
        return JsonUtil.obj(
                "id", id,
                "symbol", symbol,
                "side", side,
                "type", type,
                "quantity", quantity,
                "limitPrice", limitPrice,
                "status", status,
                "fillPrice", fillPrice,
                "fee", fee,
                "message", message,
                "createdAt", DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(createdAt)
        );
    }
}

class OrderSide {
    static final String BUY = "BUY";
    static final String SELL = "SELL";
}

class OrderType {
    static final String MARKET = "MARKET";
    static final String LIMIT = "LIMIT";
}

class OrderStatus {
    static final String NEW = "NEW";
    static final String PENDING = "PENDING";
    static final String FILLED = "FILLED";
    static final String REJECTED = "REJECTED";
}

class OrderValidator implements Validator<Order> {
    private final List<Validator<Order>> validators;

    OrderValidator(List<Validator<Order>> validators) {
        this.validators = validators;
    }

    @Override
    public void validate(Order order) {
        validators.forEach(v -> v.validate(order));
    }
}

class CashValidator implements Validator<Order> {
    private final InMemoryDatabase database;
    private final MarketDataService marketDataService;
    private final FeePolicy feePolicy;

    CashValidator(InMemoryDatabase database, MarketDataService marketDataService, FeePolicy feePolicy) {
        this.database = database;
        this.marketDataService = marketDataService;
        this.feePolicy = feePolicy;
    }

    @Override
    public void validate(Order order) {
        if (!OrderSide.BUY.equals(order.side())) {
            return;
        }
        Money price = order.limitPrice() == null ? marketDataService.quote(order.symbol()).price() : order.limitPrice();
        Money total = price.times(order.quantity()).plus(feePolicy.fee(order, price));
        if (database.demoAccount().cash().compareTo(total) < 0) {
            throw new NotEnoughCashException("매수 주문을 넣기에 예수금이 부족합니다");
        }
    }
}

class HoldingValidator implements Validator<Order> {
    private final InMemoryDatabase database;

    HoldingValidator(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public void validate(Order order) {
        if (OrderSide.SELL.equals(order.side()) && database.demoAccount().portfolio.quantity(order.symbol()) < order.quantity()) {
            throw new NotEnoughSharesException(order.symbol() + " 매도 가능 수량이 부족합니다");
        }
    }
}

class SymbolValidator implements Validator<Order> {
    private final InMemoryDatabase database;

    SymbolValidator(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public void validate(Order order) {
        if (order.symbol().isBlank() || database.stocks.findById(order.symbol()).isEmpty()) {
            throw new SymbolNotFoundException("알 수 없는 종목코드입니다: " + order.symbol());
        }
        if (!Set.of(OrderSide.BUY, OrderSide.SELL).contains(order.side())) {
            throw new ValidationException("매매구분은 BUY 또는 SELL이어야 합니다");
        }
        if (!Set.of(OrderType.MARKET, OrderType.LIMIT).contains(order.type())) {
            throw new ValidationException("주문유형은 MARKET 또는 LIMIT이어야 합니다");
        }
        if (OrderType.LIMIT.equals(order.type()) && order.limitPrice() == null) {
            throw new ValidationException("지정가 주문에는 지정가가 필요합니다");
        }
    }
}

class QuantityValidator implements Validator<Order> {
    @Override
    public void validate(Order order) {
        if (order.quantity() <= 0) {
            throw new ValidationException("수량은 1주 이상이어야 합니다");
        }
        if (order.quantity() > 10000) {
            throw new ValidationException("모의투자에서 한 번에 주문할 수 있는 수량을 초과했습니다");
        }
    }
}

interface OrderExecutor {
    Order execute(Order order);
}

class MarketOrderExecutor implements OrderExecutor {
    private final InMemoryDatabase database;
    private final MarketDataService marketDataService;
    private final FeePolicy feePolicy;
    private final TransactionLedger ledger;
    private final EventBus eventBus;
    private final AuditLogger auditLogger;

    MarketOrderExecutor(InMemoryDatabase database, MarketDataService marketDataService, FeePolicy feePolicy, TransactionLedger ledger, EventBus eventBus, AuditLogger auditLogger) {
        this.database = database;
        this.marketDataService = marketDataService;
        this.feePolicy = feePolicy;
        this.ledger = ledger;
        this.eventBus = eventBus;
        this.auditLogger = auditLogger;
    }

    @Override
    public Order execute(Order order) {
        fill(order, marketDataService.quote(order.symbol()).price());
        return order;
    }

    void fill(Order order, Money price) {
        Account account = database.demoAccount();
        Money fee = feePolicy.fee(order, price);
        Money gross = price.times(order.quantity());
        if (OrderSide.BUY.equals(order.side())) {
            account.debit(gross.plus(fee));
            account.portfolio.buy(order.symbol(), order.quantity(), price);
        } else {
            account.credit(gross.minus(fee));
            account.portfolio.sell(order.symbol(), order.quantity());
        }
        order.fill(price, fee);
        ledger.add(new Transaction(order.id(), order.symbol(), order.side(), gross, fee));
        auditLogger.log(new AuditEvent("ORDER_FILLED", order.message()));
        eventBus.publish(new OrderFilledEvent(order.id(), order.symbol(), price));
    }
}

class LimitOrderExecutor implements OrderExecutor {
    private final MarketOrderExecutor delegate;
    private final MarketDataService marketDataService;
    private final Validator<Order> fillValidator;

    LimitOrderExecutor(InMemoryDatabase database, MarketDataService marketDataService, FeePolicy feePolicy, TransactionLedger ledger, EventBus eventBus, AuditLogger auditLogger, Validator<Order> fillValidator) {
        this.delegate = new MarketOrderExecutor(database, marketDataService, feePolicy, ledger, eventBus, auditLogger);
        this.marketDataService = marketDataService;
        this.fillValidator = fillValidator;
    }

    @Override
    public Order execute(Order order) {
        Money market = marketDataService.quote(order.symbol()).price();
        boolean canFill = OrderSide.BUY.equals(order.side())
                ? market.compareTo(order.limitPrice()) <= 0
                : market.compareTo(order.limitPrice()) >= 0;
        if (canFill) {
            fillValidator.validate(order);
            delegate.fill(order, market);
        } else {
            order.pend("지정가에 도달하지 않아 주문이 대기 중입니다");
        }
        return order;
    }
}

class PendingOrderProcessor {
    private final InMemoryDatabase database;
    private final OrderExecutor limitExecutor;

    PendingOrderProcessor(InMemoryDatabase database, OrderExecutor limitExecutor) {
        this.database = database;
        this.limitExecutor = limitExecutor;
    }

    int process() {
        int filled = 0;
        for (Order order : database.orders.findAll()) {
            if (!OrderStatus.PENDING.equals(order.status())) {
                continue;
            }
            try {
                Order updated = limitExecutor.execute(order);
                database.orders.save(updated);
                if (OrderStatus.FILLED.equals(updated.status())) {
                    filled++;
                }
            } catch (ValidationException ex) {
                order.reject(ex.getMessage());
                database.orders.save(order);
            }
        }
        return filled;
    }
}

interface FeePolicy {
    Money fee(Order order, Money fillPrice);
}

class FlatFeePolicy implements FeePolicy {
    @Override
    public Money fee(Order order, Money fillPrice) {
        return Money.of(1.00);
    }
}

interface RiskService {
    AccountSnapshot snapshot();
}

class DefaultRiskService implements RiskService {
    private final InMemoryDatabase database;

    DefaultRiskService(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public AccountSnapshot snapshot() {
        Account account = database.demoAccount();
        return new AccountSnapshot(account.id(), account.cash(), Money.zero(), Money.zero(), Percent.zero());
    }
}

class Watchlist implements JsonSerializable {
    private final Set<String> symbols = new TreeSet<>();

    void add(String symbol) {
        symbols.add(symbol.toUpperCase(Locale.ROOT));
    }

    void remove(String symbol) {
        symbols.remove(symbol.toUpperCase(Locale.ROOT));
    }

    List<String> symbols() {
        return new ArrayList<>(symbols);
    }

    @Override
    public String toJson() {
        return JsonUtil.array(symbols.stream().map(JsonUtil::quote).toList());
    }
}

interface WatchlistService {
    void add(String symbol);

    void remove(String symbol);
}

class DefaultWatchlistService implements WatchlistService {
    private final InMemoryDatabase database;

    DefaultWatchlistService(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    public void add(String symbol) {
        if (database.stocks.findById(symbol.toUpperCase(Locale.ROOT)).isEmpty()) {
            throw new SymbolNotFoundException("알 수 없는 종목코드입니다: " + symbol);
        }
        database.demoAccount().watchlist.add(symbol);
    }

    @Override
    public void remove(String symbol) {
        database.demoAccount().watchlist.remove(symbol);
    }
}

class LeaderboardEntry implements JsonSerializable {
    private final String name;
    private final Money equity;
    private final Percent returnPct;

    LeaderboardEntry(String name, Money equity, Percent returnPct) {
        this.name = name;
        this.equity = equity;
        this.returnPct = returnPct;
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("name", name, "equity", equity, "returnPct", returnPct);
    }
}

interface LeaderboardService {
    List<LeaderboardEntry> entries();
}

class DefaultLeaderboardService implements LeaderboardService {
    private final InMemoryDatabase database;
    private final MarketDataService marketDataService;

    DefaultLeaderboardService(InMemoryDatabase database, MarketDataService marketDataService) {
        this.database = database;
        this.marketDataService = marketDataService;
    }

    @Override
    public List<LeaderboardEntry> entries() {
        AccountSnapshot demo = new PerformanceCalculator(marketDataService).snapshot(database.demoAccount());
        return List.of(
                new LeaderboardEntry("데모 사용자", demo.equity(), demo.returnPct()),
                new LeaderboardEntry("모멘텀 봇", Money.of(103420.80), Percent.of(3.42)),
                new LeaderboardEntry("가치투자 봇", Money.of(98770.10), Percent.of(-1.23))
        );
    }
}

interface AccountService {
    AccountSnapshot snapshot();

    String accountJson();
}

class DefaultAccountService implements AccountService {
    private final InMemoryDatabase database;
    private final MarketDataService marketDataService;

    DefaultAccountService(InMemoryDatabase database, MarketDataService marketDataService) {
        this.database = database;
        this.marketDataService = marketDataService;
    }

    @Override
    public AccountSnapshot snapshot() {
        return new PerformanceCalculator(marketDataService).snapshot(database.demoAccount());
    }

    @Override
    public String accountJson() {
        Account account = database.demoAccount();
        AccountSnapshot snapshot = snapshot();
        List<String> holdings = new ArrayList<>();
        for (Holding holding : account.portfolio.all()) {
            Quote quote = marketDataService.quote(holding.symbol());
            Money value = quote.price().times(holding.quantity());
            Money basis = holding.averageCost().times(holding.quantity());
            holdings.add(JsonUtil.obj(
                    "symbol", holding.symbol(),
                    "quantity", holding.quantity(),
                    "averageCost", holding.averageCost(),
                    "marketValue", value,
                    "gainLoss", value.minus(basis)
            ));
        }
        List<String> orders = database.orders.findAll().stream()
                .sorted(Comparator.comparing(Order::id).reversed())
                .map(Order::toJson)
                .toList();
        return JsonUtil.obj(
                "id", account.id(),
                "cash", account.cash(),
                "holdingsValue", snapshot.holdingsValue(),
                "equity", snapshot.equity(),
                "returnPct", snapshot.returnPct(),
                "watchlist", JsonUtil.array(account.watchlist.symbols().stream().map(JsonUtil::quote).toList()),
                "holdings", JsonUtil.array(holdings),
                "orders", JsonUtil.array(orders)
        );
    }
}

class ApiController implements Controller {
    private final AppContext app;

    ApiController(AppContext app) {
        this.app = app;
    }

    @Override
    public void handle(RequestContext request, ResponseWriter response) throws IOException {
        switch (request.path) {
            case "/api/quotes" -> response.json(JsonUtil.obj("ok", true, "items", JsonUtil.array(quoteJson())));
            case "/api/account" -> response.json(app.accountService.accountJson());
            case "/api/analytics" -> response.json(app.analyticsService.analyticsJson());
            case "/api/leaderboard" -> response.json(JsonUtil.obj("ok", true, "items", JsonUtil.array(app.leaderboardService.entries().stream().map(LeaderboardEntry::toJson).toList())));
            case "/api/orders" -> placeOrder(request, response);
            case "/api/watchlist/add" -> changeWatchlist(request, response, true);
            case "/api/watchlist/remove" -> changeWatchlist(request, response, false);
            case "/api/sim/tick" -> {
                app.marketDataService.tick();
                int filled = app.pendingOrderProcessor.process();
                app.stateStore.save();
                response.json(JsonUtil.obj("ok", true, "autoFilled", filled));
            }
            case "/api/import/sp500" -> {
                int count = app.sp500ImportService.importNow();
                if (count == 0) {
                    response.statusJson(502, JsonUtil.obj("ok", false, "error", "S&P 500 데이터를 가져오지 못했습니다. 네트워크 연결을 확인해주세요."));
                } else {
                    app.stateStore.save();
                    response.json(JsonUtil.obj("ok", true, "count", count));
                }
            }
            default -> response.statusJson(404, JsonUtil.obj("ok", false, "error", "API 경로를 찾을 수 없습니다"));
        }
    }

    private List<String> quoteJson() {
        List<String> rows = new ArrayList<>();
        for (Quote quote : app.marketDataService.quotes()) {
            Stock stock = app.database.stocks.findById(quote.symbol()).orElseThrow();
            rows.add(JsonUtil.obj(
                    "symbol", quote.symbol(),
                    "name", stock.name(),
                    "sector", stock.sector(),
                    "price", quote.price(),
                    "change", quote.change(),
                    "changePct", quote.changePct()
            ));
        }
        return rows;
    }

    private void placeOrder(RequestContext request, ResponseWriter response) throws IOException {
        Order order = app.tradeService.place(request.jsonBody());
        app.stateStore.save();
        response.json(JsonUtil.obj("ok", true, "message", order.message(), "order", order));
    }

    private void changeWatchlist(RequestContext request, ResponseWriter response, boolean add) throws IOException {
        String symbol = request.jsonBody().getOrDefault("symbol", "");
        if (add) {
            app.watchlistService.add(symbol);
        } else {
            app.watchlistService.remove(symbol);
        }
        app.stateStore.save();
        response.json(JsonUtil.obj("ok", true));
    }
}

class PageController implements Controller {
    private final HtmlRenderer htmlRenderer;

    PageController(HtmlRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
    }

    @Override
    public void handle(RequestContext request, ResponseWriter response) throws IOException {
        response.html(htmlRenderer.render());
    }
}

interface Controller {
    void handle(RequestContext request, ResponseWriter response) throws IOException;
}

class HttpMethod {
    static final String GET = "GET";
    static final String POST = "POST";
}

class ContentTypes {
    static final String HTML = "text/html";
    static final String JSON = "application/json";
    static final String TEXT = "text/plain";
}

class NotFoundController implements Controller {
    @Override
    public void handle(RequestContext request, ResponseWriter response) throws IOException {
        response.statusJson(404, JsonUtil.obj("ok", false, "error", "페이지를 찾을 수 없습니다"));
    }
}

class HealthController implements Controller {
    @Override
    public void handle(RequestContext request, ResponseWriter response) throws IOException {
        response.json(JsonUtil.obj("ok", true, "status", "정상"));
    }
}

class ErrorHandler {
    void write(Exception ex, ResponseWriter response) throws IOException {
        int status = ex instanceof ValidationException || ex instanceof SymbolNotFoundException ? 400 : 500;
        response.statusJson(status, JsonUtil.obj("ok", false, "error", ex.getMessage()));
    }
}

class AppException extends RuntimeException {
    AppException(String message) {
        super(message);
    }
}

class ValidationException extends AppException {
    ValidationException(String message) {
        super(message);
    }
}

class NotEnoughCashException extends ValidationException {
    NotEnoughCashException(String message) {
        super(message);
    }
}

class NotEnoughSharesException extends ValidationException {
    NotEnoughSharesException(String message) {
        super(message);
    }
}

class SymbolNotFoundException extends ValidationException {
    SymbolNotFoundException(String message) {
        super(message);
    }
}

class Money implements Comparable<Money>, JsonSerializable {
    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    static Money zero() {
        return of(0);
    }

    Money plus(Money other) {
        return of(amount.add(other.amount));
    }

    Money minus(Money other) {
        return of(amount.subtract(other.amount));
    }

    Money times(int quantity) {
        return of(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    Money divide(int divisor) {
        return of(amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP));
    }

    BigDecimal asDecimal() {
        return amount;
    }

    String format() {
        return "$" + toJsonNumber();
    }

    String toJsonNumber() {
        return amount.toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toJson() {
        return toJsonNumber();
    }
}

class Percent implements JsonSerializable {
    private final BigDecimal value;

    private Percent(BigDecimal value) {
        this.value = value.setScale(2, RoundingMode.HALF_UP);
    }

    static Percent of(double value) {
        return new Percent(BigDecimal.valueOf(value));
    }

    static Percent fromRatio(BigDecimal ratio) {
        return new Percent(ratio.multiply(BigDecimal.valueOf(100)));
    }

    static Percent zero() {
        return of(0);
    }

    String toJsonNumber() {
        return value.toPlainString();
    }

    @Override
    public String toJson() {
        return toJsonNumber();
    }
}

class NumberFormatUtil {
    static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(Objects.toString(text, "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(Objects.toString(text, "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    static BigDecimal parseDecimal(String text, BigDecimal fallback) {
        try {
            return new BigDecimal(Objects.toString(text, "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}

class Session {
    private final String id;
    private final String userId;

    Session(String id, String userId) {
        this.id = id;
        this.userId = userId;
    }

    String id() {
        return id;
    }

    String userId() {
        return userId;
    }
}

interface SessionStore {
    Session create(String userId);

    Optional<Session> find(String id);
}

class InMemorySessionStore implements SessionStore {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session create(String userId) {
        Session session = new Session(UUID.randomUUID().toString(), userId);
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<Session> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }
}

interface AuthenticationService {
    Session loginDemo();
}

class DemoAuthenticationService implements AuthenticationService {
    private final UserRepository users;
    private final SessionStore sessions;

    DemoAuthenticationService(UserRepository users, SessionStore sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    public Session loginDemo() {
        User user = users.findById("demo").orElseThrow();
        return sessions.create(user.id());
    }
}

class User implements JsonSerializable {
    private final String id;
    private final String name;

    User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    String id() {
        return id;
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("id", id, "name", name);
    }
}

interface UserRepository {
    void save(User user);

    Optional<User> findById(String id);
}

class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        users.put(user.id(), user);
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }
}

class Transaction implements JsonSerializable {
    private final String orderId;
    private final String symbol;
    private final String side;
    private final Money gross;
    private final Money fee;

    Transaction(String orderId, String symbol, String side, Money gross, Money fee) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.gross = gross;
        this.fee = fee;
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("orderId", orderId, "symbol", symbol, "side", side, "gross", gross, "fee", fee);
    }
}

class TransactionLedger {
    private final List<Transaction> transactions = new CopyOnWriteArrayList<>();

    void add(Transaction transaction) {
        transactions.add(transaction);
    }

    List<Transaction> all() {
        return List.copyOf(transactions);
    }
}

class AuditEvent {
    private final String type;
    private final String message;

    AuditEvent(String type, String message) {
        this.type = type;
        this.message = message;
    }

    String line(Clock clock) {
        return clock.displayNow() + " " + type + " " + message;
    }
}

interface AuditLogger {
    void log(AuditEvent event);
}

class ConsoleAuditLogger implements AuditLogger {
    private final Clock clock;

    ConsoleAuditLogger(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void log(AuditEvent event) {
        System.out.println(event.line(clock));
    }
}

interface EventBus {
    void publish(DomainEvent event);
}

class SimpleEventBus implements EventBus {
    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        events.add(event);
    }
}

interface DomainEvent {
    String type();
}

class OrderPlacedEvent implements DomainEvent {
    private final String orderId;

    OrderPlacedEvent(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String type() {
        return "OrderPlaced:" + orderId;
    }
}

class OrderFilledEvent implements DomainEvent {
    private final String orderId;
    private final String symbol;
    private final Money price;

    OrderFilledEvent(String orderId, String symbol, Money price) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.price = price;
    }

    @Override
    public String type() {
        return "OrderFilled:" + orderId + ":" + symbol + ":" + price.format();
    }
}

class PriceChangedEvent implements DomainEvent {
    private final String symbol;
    private final Money price;

    PriceChangedEvent(String symbol, Money price) {
        this.symbol = symbol;
        this.price = price;
    }

    @Override
    public String type() {
        return "PriceChanged:" + symbol + ":" + price.format();
    }
}

class AccountSnapshot implements JsonSerializable {
    private final String accountId;
    private final Money cash;
    private final Money holdingsValue;
    private final Money equity;
    private final Percent returnPct;

    AccountSnapshot(String accountId, Money cash, Money holdingsValue, Money equity, Percent returnPct) {
        this.accountId = accountId;
        this.cash = cash;
        this.holdingsValue = holdingsValue;
        this.equity = equity;
        this.returnPct = returnPct;
    }

    Money holdingsValue() {
        return holdingsValue;
    }

    Money equity() {
        return equity;
    }

    Percent returnPct() {
        return returnPct;
    }

    @Override
    public String toJson() {
        return JsonUtil.obj("accountId", accountId, "cash", cash, "holdingsValue", holdingsValue, "equity", equity, "returnPct", returnPct);
    }
}

class PerformanceCalculator {
    private static final Money INITIAL_EQUITY = Money.of(100000.00);
    private final MarketDataService marketDataService;

    PerformanceCalculator(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    AccountSnapshot snapshot(Account account) {
        Money holdingsValue = Money.zero();
        for (Holding holding : account.portfolio.all()) {
            holdingsValue = holdingsValue.plus(marketDataService.quote(holding.symbol()).price().times(holding.quantity()));
        }
        Money equity = account.cash().plus(holdingsValue);
        BigDecimal ratio = equity.minus(INITIAL_EQUITY).asDecimal().divide(INITIAL_EQUITY.asDecimal(), 8, RoundingMode.HALF_UP);
        return new AccountSnapshot(account.id(), account.cash(), holdingsValue, equity, Percent.fromRatio(ratio));
    }
}

class AnalyticsService {
    private final InMemoryDatabase database;
    private final MarketDataService marketDataService;
    private final FileStateStore stateStore;

    AnalyticsService(InMemoryDatabase database, MarketDataService marketDataService, FileStateStore stateStore) {
        this.database = database;
        this.marketDataService = marketDataService;
        this.stateStore = stateStore;
    }

    String analyticsJson() {
        Account account = database.demoAccount();
        AccountSnapshot snapshot = new PerformanceCalculator(marketDataService).snapshot(account);
        List<String> holdings = new ArrayList<>();
        for (Holding holding : account.portfolio.all()) {
            Quote quote = marketDataService.quote(holding.symbol());
            Money value = quote.price().times(holding.quantity());
            Money basis = holding.averageCost().times(holding.quantity());
            BigDecimal ratio = basis.compareTo(Money.zero()) == 0
                    ? BigDecimal.ZERO
                    : value.minus(basis).asDecimal().divide(basis.asDecimal(), 8, RoundingMode.HALF_UP);
            holdings.add(JsonUtil.obj(
                    "symbol", holding.symbol(),
                    "quantity", holding.quantity(),
                    "marketValue", value,
                    "gainLoss", value.minus(basis),
                    "gainLossPct", Percent.fromRatio(ratio)));
        }
        long filled = database.orders.findAll().stream().filter(order -> OrderStatus.FILLED.equals(order.status())).count();
        long pending = database.orders.findAll().stream().filter(order -> OrderStatus.PENDING.equals(order.status())).count();
        Money fees = Money.zero();
        for (Order order : database.orders.findAll()) {
            fees = fees.plus(order.fee());
        }
        List<String> history = stateStore.equityHistory().stream()
                .map(point -> {
                    String[] parts = point.split("\t", -1);
                    return JsonUtil.obj("time", parts.length > 0 ? parts[0] : "", "equity", parts.length > 1 ? Money.of(NumberFormatUtil.parseDecimal(parts[1], BigDecimal.ZERO)) : Money.zero());
                })
                .toList();
        return JsonUtil.obj(
                "equity", snapshot.equity(),
                "returnPct", snapshot.returnPct(),
                "filledOrders", filled,
                "pendingOrders", pending,
                "fees", fees,
                "holdings", JsonUtil.array(holdings),
                "equityHistory", JsonUtil.array(history));
    }
}

class AllocationCalculator {
    Map<String, Percent> allocation(Account account, MarketDataService marketDataService) {
        Money total = Money.zero();
        Map<String, Money> values = new LinkedHashMap<>();
        for (Holding holding : account.portfolio.all()) {
            Money value = marketDataService.quote(holding.symbol()).price().times(holding.quantity());
            values.put(holding.symbol(), value);
            total = total.plus(value);
        }
        Map<String, Percent> result = new LinkedHashMap<>();
        for (Map.Entry<String, Money> entry : values.entrySet()) {
            BigDecimal ratio = total.compareTo(Money.zero()) == 0
                    ? BigDecimal.ZERO
                    : entry.getValue().asDecimal().divide(total.asDecimal(), 8, RoundingMode.HALF_UP);
            result.put(entry.getKey(), Percent.fromRatio(ratio));
        }
        return result;
    }
}

class CsvExporter {
    String orders(Collection<Order> orders) {
        StringBuilder out = new StringBuilder("주문번호,종목,구분,유형,수량,상태\n");
        for (Order order : orders) {
            out.append(order.id()).append(',').append(order.symbol()).append(',').append(order.side()).append(',')
                    .append(order.type()).append(',').append(order.quantity()).append(',').append(order.status()).append('\n');
        }
        return out.toString();
    }
}

class Sorter {
    static <T> List<T> bySymbol(Collection<T> items) {
        return items.stream()
                .sorted(Comparator.comparing(item -> {
                    if (item instanceof Stock stock) {
                        return stock.symbol();
                    }
                    if (item instanceof Quote quote) {
                        return quote.symbol();
                    }
                    if (item instanceof Holding holding) {
                        return holding.symbol();
                    }
                    return item.toString();
                }))
                .toList();
    }
}

class QueryParamParser {
    static Map<String, String> parse(String query) {
        return FormParser.parse(query == null ? "" : query);
    }
}

class FileStateStore {
    private final InMemoryDatabase database;
    private final Path path = Path.of("data", "app-state.tsv");
    private final List<String> equityHistory = new CopyOnWriteArrayList<>();

    FileStateStore(InMemoryDatabase database) {
        this.database = database;
    }

    void save() {
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = new ArrayList<>();
            Account account = database.demoAccount();
            lines.add("CASH\t" + account.cash().toJsonNumber());
            for (Holding holding : account.portfolio.all()) {
                lines.add(String.join("\t", "HOLD", holding.symbol(), String.valueOf(holding.quantity()), holding.averageCost().toJsonNumber()));
            }
            for (String symbol : account.watchlist.symbols()) {
                lines.add("WATCH\t" + symbol);
            }
            for (Order order : database.orders.findAll()) {
                lines.add(String.join("\t",
                        "ORDER",
                        order.id(),
                        order.symbol(),
                        order.side(),
                        order.type(),
                        String.valueOf(order.quantity()),
                        moneyText(order.limitPrice()),
                        order.status(),
                        moneyText(order.fillPrice()),
                        moneyText(order.fee()),
                        encode(order.message())));
            }
            recordEquity();
            int start = Math.max(0, equityHistory.size() - 50);
            for (String point : equityHistory.subList(start, equityHistory.size())) {
                lines.add("EQUITY\t" + point);
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("state save failed: " + ex.getMessage());
        }
    }

    void load() {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Money cash = Money.of(100000.00);
            Portfolio portfolio = new Portfolio();
            Watchlist watchlist = new Watchlist();
            List<Order> orders = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", -1);
                if (parts.length == 0) {
                    continue;
                }
                switch (parts[0]) {
                    case "CASH" -> cash = Money.of(NumberFormatUtil.parseDecimal(value(parts, 1), BigDecimal.valueOf(100000)));
                    case "HOLD" -> {
                        String symbol = value(parts, 1);
                        int quantity = NumberFormatUtil.parseInt(value(parts, 2), 0);
                        Money averageCost = Money.of(NumberFormatUtil.parseDecimal(value(parts, 3), BigDecimal.ZERO));
                        if (!symbol.isBlank() && quantity > 0) {
                            ensureMarketRow(symbol, averageCost);
                            portfolio.buy(symbol, quantity, averageCost);
                        }
                    }
                    case "WATCH" -> {
                        String symbol = value(parts, 1);
                        if (!symbol.isBlank()) {
                            ensureMarketRow(symbol, Money.of(100));
                            watchlist.add(symbol);
                        }
                    }
                    case "ORDER" -> orders.add(Order.restore(
                            value(parts, 1),
                            value(parts, 2),
                            value(parts, 3),
                            value(parts, 4),
                            NumberFormatUtil.parseInt(value(parts, 5), 0),
                            parseMoney(value(parts, 6)),
                            value(parts, 7).isBlank() ? OrderStatus.NEW : value(parts, 7),
                            parseMoney(value(parts, 8)),
                            parseMoney(value(parts, 9)),
                            decode(value(parts, 10))));
                    case "EQUITY" -> {
                        String time = value(parts, 1);
                        String equity = value(parts, 2);
                        if (!time.isBlank() && !equity.isBlank()) {
                            equityHistory.add(time + "\t" + equity);
                        }
                    }
                    default -> {
                    }
                }
            }
            database.accounts.save(new Account("demo", cash, portfolio, watchlist));
            database.orders.clear();
            for (Order order : orders) {
                if (!order.id().isBlank()) {
                    ensureMarketRow(order.symbol(), order.limitPrice() == null ? Money.of(100) : order.limitPrice());
                    database.orders.save(order);
                }
            }
        } catch (Exception ex) {
            System.err.println("state load failed: " + ex.getMessage());
        }
    }

    List<String> equityHistory() {
        return List.copyOf(equityHistory);
    }

    private void recordEquity() {
        Money total = database.demoAccount().cash();
        for (Holding holding : database.demoAccount().portfolio.all()) {
            Money price = database.quotes.findById(holding.symbol())
                    .map(Quote::price)
                    .orElse(holding.averageCost());
            total = total.plus(price.times(holding.quantity()));
        }
        equityHistory.add(Instant.now().toString() + "\t" + total.toJsonNumber());
    }

    private void ensureMarketRow(String symbol, Money price) {
        if (database.stocks.findById(symbol).isEmpty()) {
            database.stocks.save(new Stock(symbol, symbol, "Saved"));
        }
        if (database.quotes.findById(symbol).isEmpty()) {
            database.quotes.save(new Quote(symbol, price, price));
        }
    }

    private Money parseMoney(String text) {
        return text == null || text.isBlank() ? null : Money.of(NumberFormatUtil.parseDecimal(text, BigDecimal.ZERO));
    }

    private String moneyText(Money money) {
        return money == null ? "" : money.toJsonNumber();
    }

    private String value(String[] values, int index) {
        return index < values.length ? values[index] : "";
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}

class Sp500ImportService {
    private final InMemoryDatabase database;
    private final Sp500ApiClient apiClient = new Sp500ApiClient();

    Sp500ImportService(InMemoryDatabase database) {
        this.database = database;
    }

    int importNow() {
        try {
            List<Stock> stocks = apiClient.fetch();
            for (Stock stock : stocks) {
                database.stocks.save(stock);
                if (database.quotes.findById(stock.symbol()).isEmpty()) {
                    database.quotes.save(simulatedQuote(stock.symbol()));
                }
            }
            return stocks.size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private Quote simulatedQuote(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        BigDecimal price = BigDecimal.valueOf(25 + (hash % 45000) / 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal changeRatio = BigDecimal.valueOf(((hash / 97) % 700 - 350) / 10000.0);
        BigDecimal previous = price.divide(BigDecimal.ONE.add(changeRatio), 2, RoundingMode.HALF_UP);
        return new Quote(symbol, Money.of(price), Money.of(previous));
    }
}

class Sp500ApiClient {
    private static final URI SOURCE = URI.create("https://en.wikipedia.org/wiki/List_of_S%26P_500_companies");
    private static final Pattern TABLE = Pattern.compile("<table[^>]*id=\"constituents\"[\\s\\S]*?</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile("<tr[\\s\\S]*?</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<td[\\s\\S]*?</td>", Pattern.CASE_INSENSITIVE);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    List<Stock> fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(SOURCE)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "MockStockApp/1.0 educational simulator")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("S&P 500 source returned HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    private List<Stock> parse(String html) throws IOException {
        Matcher table = TABLE.matcher(html);
        if (!table.find()) {
            throw new IOException("S&P 500 constituents table not found");
        }
        List<Stock> result = new ArrayList<>();
        Matcher rows = ROW.matcher(table.group());
        while (rows.find()) {
            List<String> cells = cells(rows.group());
            if (cells.size() >= 3) {
                String symbol = normalizeSymbol(cells.get(0));
                String name = cells.get(1);
                String sector = cells.get(2);
                if (!symbol.isBlank() && symbol.matches("[A-Z.]+")) {
                    result.add(new Stock(symbol, name, sector));
                }
            }
        }
        if (result.size() < 100) {
            throw new IOException("Too few S&P 500 rows parsed: " + result.size());
        }
        return result;
    }

    private List<String> cells(String row) {
        List<String> result = new ArrayList<>();
        Matcher matcher = CELL.matcher(row);
        while (matcher.find()) {
            result.add(clean(matcher.group()));
        }
        return result;
    }

    private String clean(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " ")
                .replace("\n", " ")
                .trim();
    }

    private String normalizeSymbol(String symbol) {
        return clean(symbol).replace(".", ".").toUpperCase(Locale.ROOT);
    }
}

interface JsonSerializable {
    String toJson();
}

interface Validator<T> {
    void validate(T value);
}

interface Repository<T, ID> {
    void save(T value);

    Optional<T> findById(ID id);

    List<T> findAll();
}

interface Mapper<I, O> {
    O map(I input);
}

class InMemoryRepository<T, ID> implements Repository<T, ID> {
    private final Map<ID, T> values = new ConcurrentHashMap<>();
    private final Mapper<T, ID> idMapper;

    InMemoryRepository(Mapper<T, ID> idMapper) {
        this.idMapper = idMapper;
    }

    @Override
    public void save(T value) {
        values.put(idMapper.map(value), value);
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(values.values());
    }

    void clear() {
        values.clear();
    }
}
