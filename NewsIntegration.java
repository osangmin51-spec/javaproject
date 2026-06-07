import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            String title = clean(matcher.group(1));
            String description = clean(matcher.group(4));
            NewsImpact impact = NewsImpact.from(title + " " + description);
            articles.add(new NewsArticle(title, clean(matcher.group(3)), description, clean(matcher.group(5)), impact));
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

class NewsImpact {
    final String label;
    final String reason;

    NewsImpact(String label, String reason) {
        this.label = label;
        this.reason = reason;
    }

    static NewsImpact from(String text) {
        String source = text == null ? "" : text;
        String[] positive = {"상승", "호실적", "수주", "증가", "성장", "돌파", "최대", "강세", "매수", "상향", "흑자", "계약", "신제품"};
        String[] negative = {"하락", "부진", "감소", "적자", "약세", "매도", "하향", "손실", "리콜", "소송", "규제", "우려", "부담"};
        int pos = count(source, positive);
        int neg = count(source, negative);
        if (pos > neg) return new NewsImpact("호재 가능", "실적, 성장, 수주, 상향 등 긍정 키워드가 더 많습니다.");
        if (neg > pos) return new NewsImpact("악재 가능", "부진, 하락, 규제, 우려 등 부정 키워드가 더 많습니다.");
        return new NewsImpact("중립", "가격 방향을 단정할 키워드가 뚜렷하지 않습니다.");
    }

    private static int count(String text, String[] words) {
        int score = 0;
        for (String word : words) {
            if (text.contains(word)) score++;
        }
        return score;
    }
}

class NewsArticle {
    final String title;
    final String link;
    final String description;
    final String pubDate;
    final NewsImpact impact;

    NewsArticle(String title, String link, String description, String pubDate, NewsImpact impact) {
        this.title = title;
        this.link = link;
        this.description = description;
        this.pubDate = pubDate;
        this.impact = impact;
    }

    String toJson() {
        return Json.obj(
                "title", title,
                "link", link,
                "description", description,
                "pubDate", pubDate,
                "impact", impact.label,
                "impactReason", impact.reason
        );
    }
}
