package external;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import util.Json;

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
