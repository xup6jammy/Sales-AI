package com.example.salesai.mcp.client;

import java.util.List;
import java.util.Map;

public record McpServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env) {

    public McpServerConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
        if (command == null || command.isBlank()) throw new IllegalArgumentException("command");
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
