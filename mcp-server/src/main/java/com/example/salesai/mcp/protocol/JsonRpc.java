package com.example.salesai.mcp.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers for building JSON-RPC 2.0 messages used by MCP. The MCP
 * stdio transport speaks newline-delimited JSON-RPC; every request has
 * an {@code id} that the response must echo. Notifications carry no
 * id and never get a response.
 *
 * <p>Standard error codes used here:
 * <ul>
 *   <li>{@code -32600} Invalid Request — the JSON is parseable but not a valid request.</li>
 *   <li>{@code -32601} Method not found — the requested method is unknown to this server.</li>
 *   <li>{@code -32602} Invalid params — required arguments are missing or malformed.</li>
 *   <li>{@code -32603} Internal error — the handler threw.</li>
 * </ul>
 */
public final class JsonRpc {

    public static final int INVALID_REQUEST  = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS   = -32602;
    public static final int INTERNAL_ERROR   = -32603;

    private JsonRpc() {}

    /** A parsed inbound request. {@code id} is null for notifications. */
    public record Request(Object id, String method, Map<String, Object> params) {

        /** True if the client did not include an {@code id} (notification). */
        public boolean isNotification() { return id == null; }
    }

    /**
     * Parse a single JSON-RPC line. Returns {@code null} for an empty
     * line (which {@link StdioTransport} will skip).
     */
    public static Request parse(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(trimmed));
        Object id = root.get("id");
        String method = MiniJson.asString(root.get("method"));
        Map<String, Object> params = root.containsKey("params")
                ? MiniJson.asObject(root.get("params"))
                : Map.of();
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Missing 'method'");
        }
        return new Request(id, method, params);
    }

    /** Build a successful response envelope. */
    public static String success(Object id, Object result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("result", result == null ? Map.of() : result);
        return MiniJson.write(envelope);
    }

    /** Build an error response envelope. */
    public static String error(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("error", err);
        return MiniJson.write(envelope);
    }
}
