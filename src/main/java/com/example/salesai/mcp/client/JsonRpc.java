package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Engine-side JSON-RPC 2.0 helper. Builds outbound request lines and
 * parses inbound response lines.
 */
public final class JsonRpc {

    private JsonRpc() {}

    public record Error(int code, String message) {}

    public record Response(Object id, Object result, Error error) {}

    /** Build a request line (newline NOT included). */
    public static String request(Object id, String method, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("method", method);
        envelope.put("params", params == null ? Map.of() : params);
        return MiniJson.write(envelope);
    }

    /** Build a notification line (no id, no response expected). */
    public static String notification(String method, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("method", method);
        envelope.put("params", params == null ? Map.of() : params);
        return MiniJson.write(envelope);
    }

    public static Response parseResponse(String line) {
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(line));
        Object id = root.get("id");
        Object result = root.get("result");
        Error error = null;
        if (root.containsKey("error")) {
            Map<String, Object> e = MiniJson.asObject(root.get("error"));
            int code = ((Number) e.getOrDefault("code", 0)).intValue();
            String msg = String.valueOf(e.getOrDefault("message", ""));
            error = new Error(code, msg);
        }
        return new Response(id, result, error);
    }
}
