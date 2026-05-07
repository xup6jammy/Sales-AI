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
public final class McpClient implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String CLIENT_NAME = "sales-ai-engine";

    private final McpServerConfig config;
    private final StdioBridge bridge;
    private final AtomicLong nextId = new AtomicLong(1);
    private volatile boolean initialized = false;
    private final Thread shutdownHook;

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

    @Override
    public void close() {
        bridge.close();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }
    }

    public boolean isInitialized() { return initialized; }
    public McpServerConfig config() { return config; }
    StdioBridge bridge() { return bridge; }
    AtomicLong nextId() { return nextId; }
}
