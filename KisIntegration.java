import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class KisConfig {
    final String appKey;
    final String appSecret;
    final String baseUrl;
    final String webSocketUrl;
    final boolean webSocketEnabled;
    final int webSocketMaxSubscriptions;
    final int marketRankLimit;
    final int pollTargetLimit;

    KisConfig(String appKey, String appSecret, String baseUrl, String webSocketUrl, boolean webSocketEnabled, int webSocketMaxSubscriptions, int marketRankLimit, int pollTargetLimit) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.webSocketUrl = webSocketUrl;
        this.webSocketEnabled = webSocketEnabled;
        this.webSocketMaxSubscriptions = webSocketMaxSubscriptions;
        this.marketRankLimit = marketRankLimit;
        this.pollTargetLimit = pollTargetLimit;
    }

    static KisConfig fromEnv() {
        String key = System.getenv("KIS_APP_KEY");
        String secret = System.getenv("KIS_APP_SECRET");
        String base = System.getenv().getOrDefault("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443");
        String wsUrl = System.getenv().getOrDefault("KIS_WS_URL", "ws://ops.koreainvestment.com:21000");
        boolean wsEnabled = "true".equalsIgnoreCase(System.getenv().getOrDefault("KIS_USE_WEBSOCKET", "false"));
        int wsMaxSubscriptions = Math.max(1, intEnv("KIS_WS_MAX_SUBSCRIPTIONS", 100));
        int marketLimit = Math.min(300, Math.max(100, intEnv("KIS_MARKET_LIMIT", 200)));
        int pollLimit = Math.min(marketLimit, Math.max(30, intEnv("KIS_POLL_LIMIT", 100)));
        if (blank(key) || blank(secret)) return null;
        return new KisConfig(key, secret, base, wsUrl, wsEnabled, wsMaxSubscriptions, marketLimit, pollLimit);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int intEnv(String key, int fallback) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

class KisQuoteTarget {
    final String name;
    final String code;

    KisQuoteTarget(String name, String code) {
        this.name = name;
        this.code = code;
    }
}

class KisQuote {
    final String name;
    final String code;
    final int price;
    final int change;
    final double changeRate;
    final long volume;

    KisQuote(String name, String code, int price, int change, double changeRate, long volume) {
        this.name = name;
        this.code = code;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
    }

    BrokerTick toTick() {
        return new BrokerTick(name, price, change, changeRate, volume);
    }
}

class KisVolumeRankItem {
    final int rank;
    final String name;
    final String code;
    final int price;
    final int change;
    final double changeRate;
    final long volume;

    KisVolumeRankItem(int rank, String name, String code, long volume) {
        this(rank, name, code, 0, 0, 0.0, volume);
    }

    KisVolumeRankItem(int rank, String name, String code, int price, int change, double changeRate, long volume) {
        this.rank = rank;
        this.name = name;
        this.code = code;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
    }

    KisQuoteTarget toTarget() {
        return new KisQuoteTarget(name, code);
    }
}

class KisVolumeRankPage {
    final List<KisVolumeRankItem> items;
    final boolean hasMore;

    KisVolumeRankPage(List<KisVolumeRankItem> items, boolean hasMore) {
        this.items = items;
        this.hasMore = hasMore;
    }
}

class KisQuoteClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final KisConfig config;
    private String accessToken;
    private long tokenIssuedAt;

    KisQuoteClient(KisConfig config) {
        this.config = config;
    }

    KisQuote inquirePrice(KisQuoteTarget target) throws Exception {
        String token = accessToken();
        String path = "/uapi/domestic-stock/v1/quotations/inquire-price";
        String query = "FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + URLEncoder.encode(target.code, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + path + "?" + query))
                .header("authorization", "Bearer " + token)
                .header("appkey", config.appKey)
                .header("appsecret", config.appSecret)
                .header("tr_id", "FHKST01010100")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("KIS 현재가 조회 실패 HTTP " + response.statusCode());
        }
        String body = response.body();
        int price = intValue(body, "stck_prpr");
        int change = intValue(body, "prdy_vrss");
        double rate = doubleValue(body, "prdy_ctrt");
        long volume = longValue(body, "acml_vol");
        if (price <= 0) throw new IllegalStateException("KIS 현재가 응답에 가격이 없습니다.");
        return new KisQuote(target.name, target.code, price, change, rate, volume);
    }

    List<KisVolumeRankItem> volumeRank(int limit) throws Exception {
        Map<String, KisVolumeRankItem> ranked = new LinkedHashMap<>();
        String trCont = "";
        for (int page = 0; page < 5 && ranked.size() < limit; page++) {
            KisVolumeRankPage rankPage = volumeRankPage(trCont, limit - ranked.size());
            for (KisVolumeRankItem item : rankPage.items) {
                ranked.putIfAbsent(item.code, item);
                if (ranked.size() >= limit) break;
            }
            if (!rankPage.hasMore) break;
            trCont = "N";
            Thread.sleep(250);
        }
        return new ArrayList<>(ranked.values());
    }

    String approvalKey() throws Exception {
        String body = Json.obj(
                "grant_type", "client_credentials",
                "appkey", config.appKey,
                "secretkey", config.appSecret
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + "/oauth2/Approval"))
                .header("content-type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("KIS WebSocket 승인키 발급 실패 HTTP " + response.statusCode());
        }
        String key = stringValue(response.body(), "approval_key");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("KIS WebSocket 승인키 응답에 approval_key가 없습니다.");
        }
        return key;
    }

    private KisVolumeRankPage volumeRankPage(String trCont, int limit) throws Exception {
        String token = accessToken();
        String path = "/uapi/domestic-stock/v1/quotations/volume-rank";
        String query = String.join("&",
                "FID_COND_MRKT_DIV_CODE=J",
                "FID_COND_SCR_DIV_CODE=20171",
                "FID_INPUT_ISCD=0000",
                "FID_DIV_CLS_CODE=0",
                "FID_BLNG_CLS_CODE=0",
                "FID_TRGT_CLS_CODE=111111111",
                "FID_TRGT_EXLS_CLS_CODE=000000",
                "FID_INPUT_PRICE_1=",
                "FID_INPUT_PRICE_2=",
                "FID_VOL_CNT=",
                "FID_INPUT_DATE_1="
        );
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl + path + "?" + query))
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + token)
                .header("appkey", config.appKey)
                .header("appsecret", config.appSecret)
                .header("tr_id", "FHPST01710000")
                .header("custtype", "P")
                .GET();
        if (!trCont.isBlank()) {
            builder.header("tr_cont", trCont);
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("KIS 거래량 순위 조회 실패 HTTP " + response.statusCode());
        }
        String next = response.headers().firstValue("tr_cont").orElse("").trim();
        return new KisVolumeRankPage(parseVolumeRank(response.body(), limit), next.equals("M") || next.equals("F"));
    }

    private String accessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (accessToken != null && now - tokenIssuedAt < 23L * 60L * 60L * 1000L) {
            return accessToken;
        }
        String body = Json.obj(
                "grant_type", "client_credentials",
                "appkey", config.appKey,
                "appsecret", config.appSecret
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + "/oauth2/tokenP"))
                .header("content-type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("KIS 토큰 발급 실패 HTTP " + response.statusCode());
        }
        accessToken = stringValue(response.body(), "access_token");
        tokenIssuedAt = now;
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("KIS 토큰 응답에 access_token이 없습니다.");
        }
        return accessToken;
    }

    private String stringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<KisVolumeRankItem> parseVolumeRank(String json, int limit) {
        List<KisVolumeRankItem> items = new ArrayList<>();
        Matcher array = Pattern.compile("\"output\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!array.find()) return items;
        Matcher object = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL).matcher(array.group(1));
        while (object.find() && items.size() < limit) {
            String body = object.group(1);
            String name = stringValue(body, "hts_kor_isnm");
            String code = stringValue(body, "mksc_shrn_iscd");
            if (name.isBlank() || code.isBlank()) continue;
            int rank = intValue(body, "data_rank");
            if (rank <= 0) rank = items.size() + 1;
            int price = firstInt(body, "stck_prpr", "avrg_prpr", "stck_prdy_clpr");
            int change = firstInt(body, "prdy_vrss", "prdy_vrss_sign");
            double rate = firstDouble(body, "prdy_ctrt", "stck_prdy_ctrt");
            items.add(new KisVolumeRankItem(rank, name, code, price, change, rate, longValue(body, "acml_vol")));
        }
        return items;
    }

    private int firstInt(String json, String... keys) {
        for (String key : keys) {
            int value = intValue(json, key);
            if (value != 0) return value;
        }
        return 0;
    }

    private double firstDouble(String json, String... keys) {
        for (String key : keys) {
            double value = doubleValue(json, key);
            if (value != 0.0) return value;
        }
        return 0.0;
    }

    private int intValue(String json, String key) {
        String value = stringValue(json, key).replace(",", "").trim();
        if (value.isBlank()) return 0;
        return Integer.parseInt(value);
    }

    private long longValue(String json, String key) {
        String value = stringValue(json, key).replace(",", "").trim();
        if (value.isBlank()) return 0L;
        return Long.parseLong(value);
    }

    private double doubleValue(String json, String key) {
        String value = stringValue(json, key).replace(",", "").trim();
        if (value.isBlank()) return 0.0;
        return Double.parseDouble(value);
    }
}

class KisWebSocketQuoteClient implements WebSocket.Listener {
    private final KisQuoteClient quoteClient;
    private final KisConfig config;
    private final Map<String, KisQuoteTarget> targets;
    private final Consumer<BrokerTick> consumer;
    private final Consumer<List<KisVolumeRankItem>> volumeRankConsumer;
    private final Runnable unavailableCallback;
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final StringBuilder buffer = new StringBuilder();
    private volatile WebSocket webSocket;

    KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer) {
        this(config, targets, consumer, ranks -> {});
    }

    KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer) {
        this(config, targets, consumer, volumeRankConsumer, () -> {});
    }

    KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer, Runnable unavailableCallback) {
        this.config = config;
        this.targets = new LinkedHashMap<>(targets);
        this.consumer = consumer;
        this.volumeRankConsumer = volumeRankConsumer;
        this.unavailableCallback = unavailableCallback;
        this.quoteClient = new KisQuoteClient(config);
    }

    boolean start() {
        try {
            refreshVolumeRankTargets();
            String approvalKey = quoteClient.approvalKey();
            webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(config.webSocketUrl), this)
                    .join();
            if (!openLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("WebSocket 연결 시간이 초과되었습니다.");
            }
            subscribe(approvalKey);
            return true;
        } catch (Exception ex) {
            System.err.println("KIS WebSocket 시작 실패: " + ex.getMessage());
            return false;
        }
    }

    private void refreshVolumeRankTargets() {
        try {
            List<KisVolumeRankItem> ranked = quoteClient.volumeRank(config.marketRankLimit);
            if (ranked.isEmpty()) return;
            volumeRankConsumer.accept(ranked);
            targets.clear();
            for (KisVolumeRankItem item : ranked) {
                if (targets.size() >= config.pollTargetLimit) break;
                targets.putIfAbsent(item.name, item.toTarget());
            }
            System.out.println("KIS WebSocket 구독 대상 거래량 상위 자동 선별: " + targets.size() + "개");
        } catch (Exception ex) {
            System.err.println("KIS WebSocket 거래량 순위 선별 실패, 기존 종목으로 구독 시도: " + ex.getMessage());
        }
    }

    private void subscribe(String approvalKey) {
        int limit = Math.min(config.webSocketMaxSubscriptions, targets.size());
        targets.values().stream().limit(limit).forEach(target -> {
            String message = Json.obj(
                    "header", Json.obj(
                            "approval_key", approvalKey,
                            "custtype", "P",
                            "tr_type", "1",
                            "content-type", "utf-8"
                    ),
                    "body", Json.obj(
                            "input", Json.obj(
                                    "tr_id", "H0STCNT0",
                                    "tr_key", target.code
                            )
                    )
            );
            webSocket.sendText(message, true);
        });
        System.out.println("KIS WebSocket 국내주식 체결 구독 시도: " + limit + "개 종목");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
        openLatch.countDown();
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            handleMessage(buffer.toString());
            buffer.setLength(0);
        }
        webSocket.request(1);
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("KIS WebSocket 오류: " + error.getMessage());
        unavailableCallback.run();
    }

    private void handleMessage(String message) {
        BrokerTick tick = parseTick(message);
        if (tick != null) {
            consumer.accept(tick);
        }
    }

    private BrokerTick parseTick(String message) {
        try {
            if (!message.contains("H0STCNT0")) return null;
            String[] blocks = message.split("\\|", -1);
            if (blocks.length < 4) return null;
            String payload = blocks[3];
            String[] values = payload.split("\\^", -1);
            if (values.length < 15) return null;
            String code = values[0];
            KisQuoteTarget target = targets.values().stream().filter(item -> item.code.equals(code)).findFirst().orElse(null);
            if (target == null) return null;
            int price = parseInt(values[2]);
            int change = values.length > 5 ? parseInt(values[5]) : 0;
            double rate = values.length > 5 && price - change != 0 ? change * 100.0 / (price - change) : 0.0;
            long volume = values.length > 13 ? parseLong(values[13]) : 0;
            if (price <= 0) return null;
            return new BrokerTick(target.name, price, change, rate, volume);
        } catch (Exception ex) {
            return null;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.replace(",", "").trim());
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        return Long.parseLong(value.replace(",", "").trim());
    }
}

class KisQuotePoller {
    private final KisQuoteClient client;
    private final Map<String, KisQuoteTarget> fallbackTargets;
    private final Consumer<BrokerTick> consumer;
    private final Consumer<List<KisVolumeRankItem>> volumeRankConsumer;
    private final Runnable unavailableCallback;
    private final int marketRankLimit;
    private final int pollTargetLimit;
    private Map<String, KisQuoteTarget> activeTargets = new LinkedHashMap<>();
    private long lastRankRefresh;
    private volatile boolean running = true;
    private volatile boolean unavailableNotified;

    KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer) {
        this(config, targets, consumer, ranks -> {});
    }

    KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer) {
        this(config, targets, consumer, volumeRankConsumer, () -> {});
    }

    KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer, Runnable unavailableCallback) {
        this.client = new KisQuoteClient(config);
        this.fallbackTargets = new LinkedHashMap<>(targets);
        this.consumer = consumer;
        this.volumeRankConsumer = volumeRankConsumer;
        this.unavailableCallback = unavailableCallback;
        this.marketRankLimit = config.marketRankLimit;
        this.pollTargetLimit = config.pollTargetLimit;
    }

    void start() {
        Thread thread = new Thread(this::pollLoop, "kis-quote-poller");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollLoop() {
        System.out.println("한국투자증권 KIS 현재가 연동 시작: " + LocalDateTime.now());
        while (running) {
            for (KisQuoteTarget target : refreshTargets().values()) {
                pollTarget(target);
            }
        }
    }

    private synchronized Map<String, KisQuoteTarget> refreshTargets() {
        long now = System.currentTimeMillis();
        if (!activeTargets.isEmpty() && now - lastRankRefresh < 10L * 60L * 1000L) {
            return new LinkedHashMap<>(activeTargets);
        }
        try {
            List<KisVolumeRankItem> ranked = client.volumeRank(marketRankLimit);
            if (!ranked.isEmpty()) {
                volumeRankConsumer.accept(ranked);
                LinkedHashMap<String, KisQuoteTarget> nextTargets = new LinkedHashMap<>();
                ranked.stream().limit(pollTargetLimit).forEach(item -> nextTargets.putIfAbsent(item.name, item.toTarget()));
                fallbackTargets.values().forEach(target -> {
                    if (nextTargets.size() < pollTargetLimit) nextTargets.putIfAbsent(target.name, target);
                });
                activeTargets = nextTargets;
                lastRankRefresh = now;
                System.out.println("KIS 거래량 상위 종목 자동 선별: " + ranked.size() + "개 표시, " + activeTargets.size() + "개 현재가 폴링");
                return new LinkedHashMap<>(activeTargets);
            }
        } catch (Exception ex) {
            System.err.println("KIS 거래량 순위 조회 실패: " + ex.getMessage());
            notifyUnavailable();
        }
        if (activeTargets.isEmpty()) {
            fallbackTargets.values().forEach(target -> {
                if (activeTargets.size() < pollTargetLimit) activeTargets.putIfAbsent(target.name, target);
            });
        }
        return new LinkedHashMap<>(activeTargets);
    }

    private void pollTarget(KisQuoteTarget target) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                consumer.accept(client.inquirePrice(target).toTick());
                Thread.sleep(700);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
                return;
            } catch (Exception ex) {
                if (attempt == 3) {
                    System.err.println("KIS 현재가 조회 실패(" + target.name + "): " + ex.getMessage());
                    notifyUnavailable();
                    sleep(2_000);
                    return;
                }
                sleep(600);
            }
        }
    }

    private void notifyUnavailable() {
        if (unavailableNotified) return;
        unavailableNotified = true;
        unavailableCallback.run();
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
