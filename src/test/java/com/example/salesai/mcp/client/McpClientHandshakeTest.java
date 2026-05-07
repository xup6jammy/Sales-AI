package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class McpClientHandshakeTest {
    public static void main(String[] args) throws Exception {
        new McpClientHandshakeTest().run();
    }

    void run() throws Exception {
        testInitializeReturnsProtocolVersion();
        System.out.println("McpClientHandshakeTest: 1 passed");
    }

    void testInitializeReturnsProtocolVersion() throws Exception {
        McpServerConfig cfg = new McpServerConfig(
            "fake", fakeCommand(), fakeArgs(), Map.of());
        try (McpClient client = McpClient.spawn(cfg)) {
            McpClient.InitResult r = client.initialize(2000);
            assert "2025-06-18".equals(r.protocolVersion());
            assert "fake".equals(r.serverName());
        }
    }

    private static String fakeCommand() {
        return win() ? "cmd.exe" : "sh";
    }

    private static List<String> fakeArgs() {
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        if (win()) {
            return List.of("/c", here.resolve("fake-mcp-server.cmd").toString());
        }
        return List.of(here.resolve("fake-mcp-server.sh").toString());
    }

    private static boolean win() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
