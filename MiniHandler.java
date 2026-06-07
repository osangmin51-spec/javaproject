import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
