import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MiniHandler implements HttpHandler {
    private final MiniProject project;

    MiniHandler(MiniProject project) {
        this.project = project;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String sessionId = sessionCookie(exchange);
        try {
            if ("GET".equals(exchange.getRequestMethod()) && "/".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", MiniDashboardPage.render());
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/state".equals(path)) {
                send(exchange, 200, "application/json; charset=utf-8", project.stateJson(sessionId));
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> body = Json.parseObject(readBody(exchange));
                String json = switch (path) {
                    case "/api/register" -> project.register(body);
                    case "/api/login" -> project.login(body);
                    case "/api/logout" -> project.logout(sessionId);
                    case "/api/stock/buy" -> project.buyStock(body, sessionId);
                    case "/api/stock/sell" -> project.sellStock(body, sessionId);
                    case "/api/board/write" -> project.writePost(body, sessionId);
                    case "/api/board/delete" -> project.deletePost(body, sessionId);
                    case "/api/comment/write" -> project.writeComment(body, sessionId);
                    case "/api/comment/delete" -> project.deleteComment(body, sessionId);
                    default -> Json.obj("ok", false, "error", "없는 API입니다.");
                };
                if ("/api/login".equals(path) && json.contains("\"ok\":true")) {
                    String issuedSession = jsonValue(json, "session");
                    if (!issuedSession.isBlank()) {
                        exchange.getResponseHeaders().add("Set-Cookie", "MSTOCK_SESSION=" + issuedSession + "; Path=/; HttpOnly; SameSite=Lax");
                    }
                }
                if ("/api/logout".equals(path)) {
                    exchange.getResponseHeaders().add("Set-Cookie", "MSTOCK_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                }
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

    private String sessionCookie(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return "";
        for (String part : cookie.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "MSTOCK_SESSION".equals(pair[0])) {
                return pair[1];
            }
        }
        return "";
    }

    private String jsonValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }
}
