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
