import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class Json {
    static String obj(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) out.append(',');
            out.append(quote(String.valueOf(pairs[i]))).append(':').append(value(pairs[i + 1]));
        }
        return out.append('}').toString();
    }

    static String array(Collection<String> values) {
        return "[" + String.join(",", values) + "]";
    }

    static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof String text && (text.startsWith("{") || text.startsWith("["))) return text;
        return quote(String.valueOf(value));
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static Map<String, String> parseObject(String json) {
        Map<String, String> map = new HashMap<>();
        String body = json == null ? "" : json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        for (String part : body.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            map.put(unquote(part.substring(0, colon)), unquote(part.substring(colon + 1)));
        }
        return map;
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        return URLDecoder.decode(text.replace("\\\"", "\"").replace("\\n", "\n"), StandardCharsets.UTF_8);
    }
}
