package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class StdioBridgeTest {
    public static void main(String[] args) throws Exception {
        new StdioBridgeTest().run();
    }

    void run() throws Exception {
        testEchoesLines();
        testTimeoutFiresWhenNoData();
        System.out.println("StdioBridgeTest: 2 passed");
    }

    void testEchoesLines() throws Exception {
        StdioBridge b = StdioBridge.spawn(echoCommand(), java.util.Map.of());
        try {
            b.send("hello");
            String got = b.readNextLine(2000);
            assert "hello".equals(got) : "got=" + got;
        } finally {
            b.close();
        }
    }

    void testTimeoutFiresWhenNoData() throws Exception {
        StdioBridge b = StdioBridge.spawn(echoCommand(), java.util.Map.of());
        try {
            try {
                b.readNextLine(200);
                throw new AssertionError("expected timeout");
            } catch (java.util.concurrent.TimeoutException expected) {
                // ok
            }
        } finally {
            b.close();
        }
    }

    private static java.util.List<String> echoCommand() {
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        if (win) {
            return java.util.List.of(
                "cmd.exe", "/c",
                here.resolve("echo-server.cmd").toString());
        }
        return java.util.List.of(
            "sh", here.resolve("echo-server.sh").toString());
    }
}
