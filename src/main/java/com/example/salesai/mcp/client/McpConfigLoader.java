package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpConfigLoader {

    private McpConfigLoader() {}

    public static Map<String, McpServerConfig> load(Path file) {
        if (file == null || !Files.exists(file)) return Map.of();
        String body;
        try {
            body = Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + file, e);
        }
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(body));
        Object servers = root.get("mcpServers");
        if (!(servers instanceof Map)) return Map.of();
        Map<String, Object> map = MiniJson.asObject(servers);

        Map<String, McpServerConfig> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String name = e.getKey();
            Map<String, Object> cfg = MiniJson.asObject(e.getValue());
            String command = MiniJson.asString(cfg.get("command"));
            List<String> args = toStringList(cfg.get("args"));
            Map<String, String> env = toStringMap(cfg.get("env"));
            out.put(name, new McpServerConfig(name, command, args, env));
        }
        return out;
    }

    private static List<String> toStringList(Object o) {
        if (o == null) return List.of();
        java.util.List<?> raw = (java.util.List<?>) o;
        java.util.List<String> r = new java.util.ArrayList<>(raw.size());
        for (Object x : raw) r.add(String.valueOf(x));
        return r;
    }

    private static Map<String, String> toStringMap(Object o) {
        if (o == null) return Map.of();
        Map<String, Object> raw = MiniJson.asObject(o);
        Map<String, String> r = new LinkedHashMap<>();
        for (var e : raw.entrySet()) r.put(e.getKey(), String.valueOf(e.getValue()));
        return r;
    }
}
