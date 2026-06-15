package external;

public class KisConfig {
    public final String appKey;
    public final String appSecret;
    public final String baseUrl;
    public final String webSocketUrl;
    public final boolean webSocketEnabled;
    public final int webSocketMaxSubscriptions;
    public final int marketRankLimit;
    public final int pollTargetLimit;

    public KisConfig(String appKey, String appSecret, String baseUrl, String webSocketUrl, boolean webSocketEnabled, int webSocketMaxSubscriptions, int marketRankLimit, int pollTargetLimit) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.webSocketUrl = webSocketUrl;
        this.webSocketEnabled = webSocketEnabled;
        this.webSocketMaxSubscriptions = webSocketMaxSubscriptions;
        this.marketRankLimit = marketRankLimit;
        this.pollTargetLimit = pollTargetLimit;
    }

    public static KisConfig fromEnv() {
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
