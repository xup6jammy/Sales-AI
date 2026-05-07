package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP client. Spawns a server subprocess, performs the JSON-RPC
 * initialize handshake, then exposes tools/list and tools/call
 * (added in Task 2.6).
 *
 * <p>Not thread-safe — one client per workflow run.
 */
public class McpClient implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String CLIENT_NAME = "sales-ai-engine";

    private McpServerConfig config;
    private StdioBridge bridge;
    private final AtomicLong nextId = new AtomicLong(1);
    private volatile boolean initialized = false;
    private Thread shutdownHook;

    public record InitResult(String protocolVersion, String serverName) {}

    public static McpClient spawn(McpServerConfig config) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add(config.command());
        command.addAll(config.args());
        StdioBridge bridge = StdioBridge.spawn(command, config.env());
        return new McpClient(config, bridge);
    }

    private McpClient(McpServerConfig config, StdioBridge bridge) {
        this.config = config;
        this.bridge = bridge;
        // JVM shutdown hook: kill the child if the engine exits unexpectedly.
        this.shutdownHook = new Thread(this::close, "mcp-shutdown-" + config.name());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /** Test-only — subclasses can override callToolText/listToolNames without spawning. */
    protected McpClient() {
        this.config = new McpServerConfig("test", "noop", java.util.List.of(), java.util.Map.of());
        this.bridge = null;
        this.shutdownHook = null;
    }

    public InitResult initialize(long timeoutMs) throws IOException {
        long id = nextId.getAndIncrement();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", CLIENT_NAME);
        info.put("version", "0.1.0");
        params.put("clientInfo", info);

        bridge.send(JsonRpc.request(id, "initialize", params));
        JsonRpc.Response r = readResponseFor(id, timeoutMs);
        if (r.error() != null) {
            throw new IOException("initialize error " + r.error().code()
                + ": " + r.error().message());
        }
        Map<String, Object> result = MiniJson.asObject(r.result());
        String protoVersion = MiniJson.asString(result.get("protocolVersion"));
        Map<String, Object> serverInfo = MiniJson.asObject(
            result.getOrDefault("serverInfo", Map.of()));
        String serverName = MiniJson.asString(serverInfo.getOrDefault("name", ""));
        if (!PROTOCOL_VERSION.equals(protoVersion)) {
            System.err.println("[mcp-warn] server " + serverName
                + " negotiated protocol " + protoVersion
                + ", client expected " + PROTOCOL_VERSION);
        }

        // Send the required initialized notification.
        bridge.send(JsonRpc.notification("notifications/initialized", Map.of()));
        initialized = true;
        return new InitResult(protoVersion, serverName);
    }

    private JsonRpc.Response readResponseFor(long expectedId, long timeoutMs)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) throw new IOException("timeout waiting for response id=" + expectedId);
            String line;
            try {
                line = bridge.readNextLine(remaining);
            } catch (TimeoutException te) {
                throw new IOException("timeout waiting for response id=" + expectedId);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            JsonRpc.Response r = JsonRpc.parseResponse(line);
            // Skip notifications and unrelated responses.
            if (r.id() == null) continue;
            if (String.valueOf(r.id()).equals(String.valueOf(expectedId))) return r;
        }
    }

    /**
     * Calls {@code tools/list} and returns just the tool names. Full schema
     * isn't needed by the engine since we know each adapter's expected tool
     * names ahead of time (see EmailMcpToolMapping).
     */
    public List<String> listToolNames(long timeoutMs) throws IOException {
        if (!initialized) throw new IllegalStateException("not initialized");
        long id = nextId.getAndIncrement();
        bridge.send(JsonRpc.request(id, "tools/list", Map.of()));
        JsonRpc.Response r = readResponseFor(id, timeoutMs);
        if (r.error() != null) {
            throw new IOException("tools/list error: " + r.error().message());
        }
        Map<String, Object> result = MiniJson.asObject(r.result());
        Object toolsRaw = result.getOrDefault("tools", List.of());
        List<?> tools = (List<?>) toolsRaw;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Object t : tools) {
            Map<String, Object> tm = MiniJson.asObject(t);
            String name = MiniJson.asString(tm.get("name"));
            if (name != null) names.add(name);
        }
        return names;
    }

    /**
     * Calls {@code tools/call} and returns the concatenated text content.
     * MCP tools may return multiple content items; this joins all
     * {@code type:"text"} items with newlines and ignores other types.
     */
    public String callToolText(String name, Map<String, Object> arguments, long timeoutMs)
            throws IOException {
        if (!initialized) throw new IllegalStateException("not initialized");
        long id = nextId.getAndIncrement();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        bridge.send(JsonRpc.request(id, "tools/call", params));
        JsonRpc.Response r = readResponseFor(id, timeoutMs);
        if (r.error() != null) {
            throw new IOException("tools/call(" + name + ") error: " + r.error().message());
        }
        Map<String, Object> result = MiniJson.asObject(r.result());
        Object contentRaw = result.getOrDefault("content", List.of());
        List<?> content = (List<?>) contentRaw;
        StringBuilder sb = new StringBuilder();
        for (Object c : content) {
            Map<String, Object> cm = MiniJson.asObject(c);
            if ("text".equals(cm.get("type"))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(MiniJson.asString(cm.get("text")));
            }
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (bridge != null) bridge.close();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM already shutting down
            }
        }
    }

    public boolean isInitialized() { return initialized; }
    public McpServerConfig config() { return config; }
    StdioBridge bridge() { return bridge; }
    AtomicLong nextId() { return nextId; }
}
