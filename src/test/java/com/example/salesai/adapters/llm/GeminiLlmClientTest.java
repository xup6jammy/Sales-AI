package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class GeminiLlmClientTest {
    public static void main(String[] args) throws Exception {
        new GeminiLlmClientTest().run();
    }

    void run() throws Exception {
        testCallsGenerateContentAndParses();
        System.out.println("GeminiLlmClientTest: 1 passed");
    }

    void testCallsGenerateContentAndParses() throws Exception {
        AtomicReference<String> queryRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-1.5-pro-002:generateContent", ex -> {
            queryRef.set(ex.getRequestURI().getQuery());
            bodyRef.set(new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            String resp = """
                {"candidates":[{"content":{"parts":[{"text":"hi from gemini"}]}}],
                 "usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            GeminiLlmClient c = new GeminiLlmClient("test-key", base);
            LlmResponse resp = c.complete(new LlmRequest(
                "sys", "hi", 100, 0.5, "gemini-1.5-pro-002"));
            assert "hi from gemini".equals(resp.text()) : resp.text();
            assert resp.inputTokens() == 7;
            assert resp.outputTokens() == 3;
            assert queryRef.get().contains("key=test-key");
            assert bodyRef.get().contains("\"text\":\"hi\"");
        } finally {
            server.stop(0);
        }
    }
}
