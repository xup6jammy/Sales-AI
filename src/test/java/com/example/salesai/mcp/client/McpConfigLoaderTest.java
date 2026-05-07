package com.example.salesai.mcp.client;

import java.nio.file.Files;
import java.nio.file.Path;

public final class McpConfigLoaderTest {
    public static void main(String[] args) throws Exception {
        new McpConfigLoaderTest().run();
    }

    void run() throws Exception {
        testLoadsClaudeCodeFormat();
        testReturnsEmptyWhenFileMissing();
        testThrowsOnMalformedJson();
        System.out.println("McpConfigLoaderTest: 3 passed");
    }

    void testLoadsClaudeCodeFormat() throws Exception {
        Path tmp = Files.createTempFile("mcp-config", ".json");
        Files.writeString(tmp, """
            {
              "mcpServers": {
                "gmail": {
                  "command": "npx",
                  "args": ["-y", "@gongrzhe/server-gmail-autoauth-mcp"],
                  "env": {"GMAIL_CACHE": "/tmp/g"}
                },
                "outlook": {
                  "command": "uvx",
                  "args": ["mcp-server-outlook"]
                }
              }
            }
            """);
        var configs = McpConfigLoader.load(tmp);
        assert configs.size() == 2;
        assert "npx".equals(configs.get("gmail").command());
        assert configs.get("gmail").args().size() == 2;
        assert "/tmp/g".equals(configs.get("gmail").env().get("GMAIL_CACHE"));
        assert "uvx".equals(configs.get("outlook").command());
    }

    void testReturnsEmptyWhenFileMissing() {
        var configs = McpConfigLoader.load(Path.of("/no/such/file.json"));
        assert configs.isEmpty();
    }

    void testThrowsOnMalformedJson() throws Exception {
        Path tmp = Files.createTempFile("mcp-bad", ".json");
        Files.writeString(tmp, "{ not json");
        try {
            McpConfigLoader.load(tmp);
            throw new AssertionError("expected exception");
        } catch (RuntimeException ok) {}
    }
}
