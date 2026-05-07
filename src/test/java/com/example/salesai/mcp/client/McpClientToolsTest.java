package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class McpClientToolsTest {
    public static void main(String[] args) throws Exception {
        new McpClientToolsTest().run();
    }

    void run() throws Exception {
        testListToolsReturnsToolNames();
        testCallToolReturnsContentText();
        System.out.println("McpClientToolsTest: 2 passed");
    }

    void testListToolsReturnsToolNames() throws Exception {
        try (McpClient client = handshakeFake()) {
            List<String> names = client.listToolNames(2000);
            assert names.contains("fake.echo") : names.toString();
        }
    }

    void testCallToolReturnsContentText() throws Exception {
        try (McpClient client = handshakeFake()) {
            String text = client.callToolText("fake.echo", Map.of("input", "hi"), 2000);
            assert "fake-result".equals(text) : text;
        }
    }

    private static McpClient handshakeFake() throws java.io.IOException {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        McpServerConfig cfg = new McpServerConfig(
            "fake",
            win ? "cmd.exe" : "sh",
            win
                ? List.of("/c", here.resolve("fake-mcp-server.cmd").toString())
                : List.of(here.resolve("fake-mcp-server.sh").toString()),
            Map.of());
        McpClient c = McpClient.spawn(cfg);
        c.initialize(2000);
        return c;
    }
}
