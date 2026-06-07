import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KisConfig {
    final String appKey;
    final String appSecret;
    final String baseUrl;

    KisConfig(String appKey, String appSecret, String baseUrl) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
    }

    static KisConfig fromEnv() {
        String key = System.getenv("KIS_APP_KEY");
        String secret = System.getenv("KIS_APP_SECRET");
        String base = System.getenv().getOrDefault("KIS_BASE_URL", "https://openapi.koreainvestment.com:9443");
        if (blank(key) || blank(secret)) return null;
        return new KisConfig(key, secret, base);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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

class KisQuotePoller {
    private final KisQuoteClient client;
    private final Map<String, KisQuoteTarget> targets;
    private final Consumer<BrokerTick> consumer;
    private volatile boolean running = true;

    KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer) {
        this.client = new KisQuoteClient(config);
        this.targets = new LinkedHashMap<>(targets);
        this.consumer = consumer;
    }

    void start() {
        Thread thread = new Thread(this::pollLoop, "kis-quote-poller");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollLoop() {
        System.out.println("한국투자증권 KIS 현재가 연동 시작: " + LocalDateTime.now());
        while (running) {
            for (KisQuoteTarget target : targets.values()) {
                pollTarget(target);
            }
        }
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
                    sleep(2_000);
                    return;
                }
                sleep(600);
            }
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
