package com.example.salesai.mcp.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Newline-delimited JSON-RPC over stdin/stdout. This is the transport
 * Claude Code uses to talk to MCP servers it spawns as child processes.
 *
 * <p>Each inbound line is parsed as a {@link JsonRpc.Request} and
 * passed to the handler. Notifications (no id) get no response.
 * Requests get exactly one response on stdout. Diagnostics go to
 * stderr so they never collide with the protocol stream.
 */
public final class StdioTransport {

    private final Function<JsonRpc.Request, Object> handler;

    /**
     * @param handler returns the {@code result} payload for a request,
     *                or {@code null} for a notification. To signal an
     *                error, throw a {@link RpcException}.
     */
    public StdioTransport(Function<JsonRpc.Request, Object> handler) {
        this.handler = handler;
    }

    public void run() {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(
                new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8),
                /*autoFlush=*/true);

        String line;
        try {
            while ((line = in.readLine()) != null) {
                handleOne(line, out);
            }
        } catch (IOException ioe) {
            System.err.println("[mcp] stdin read failed: " + ioe.getMessage());
        }
    }

    private void handleOne(String line, PrintWriter out) {
        JsonRpc.Request req;
        try {
            req = JsonRpc.parse(line);
        } catch (RuntimeException re) {
            // Could not parse the line — emit an error with null id.
            out.println(JsonRpc.error(null, JsonRpc.INVALID_REQUEST,
                    "Parse error: " + re.getMessage()));
            return;
        }
        if (req == null) return;  // empty line

        try {
            Object result = handler.apply(req);
            if (req.isNotification()) {
                // Spec: notifications get no response.
                return;
            }
            out.println(JsonRpc.success(req.id(), result));
        } catch (RpcException rpc) {
            if (req.isNotification()) {
                System.err.println("[mcp] notification handler threw: "
                        + rpc.getMessage());
                return;
            }
            out.println(JsonRpc.error(req.id(), rpc.code(), rpc.getMessage()));
        } catch (RuntimeException re) {
            if (req.isNotification()) {
                System.err.println("[mcp] notification handler threw: "
                        + re.getMessage());
                return;
            }
            out.println(JsonRpc.error(req.id(), JsonRpc.INTERNAL_ERROR,
                    re.getClass().getSimpleName() + ": " + re.getMessage()));
        }
    }

    /** Throw from a handler to send a JSON-RPC error response. */
    public static final class RpcException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int code;
        public RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
        public int code() { return code; }
    }
}
