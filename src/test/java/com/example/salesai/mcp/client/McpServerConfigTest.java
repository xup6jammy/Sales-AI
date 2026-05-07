package com.example.salesai.mcp.client;

import java.util.List;
import java.util.Map;

public final class McpServerConfigTest {
    public static void main(String[] args) {
        new McpServerConfigTest().run();
    }

    void run() {
        testRecordHoldsAllFields();
        testRequiresCommand();
        System.out.println("McpServerConfigTest: 2 passed");
    }

    void testRecordHoldsAllFields() {
        McpServerConfig c = new McpServerConfig(
            "gmail", "npx", List.of("-y", "@gongrzhe/server-gmail-autoauth-mcp"),
            Map.of());
        assert "gmail".equals(c.name());
        assert "npx".equals(c.command());
        assert c.args().size() == 2;
        assert c.env().isEmpty();
    }

    void testRequiresCommand() {
        try {
            new McpServerConfig("gmail", "  ", List.of(), Map.of());
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException ok) {}
    }
}
