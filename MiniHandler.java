import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> body = Json.parseObject(readBody(exchange));
                String json = switch (path) {
                    case "/api/stock/buy" -> project.buyStock(body);
                    case "/api/stock/sell" -> project.sellStock(body);
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
