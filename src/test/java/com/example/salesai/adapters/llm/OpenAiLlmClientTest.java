package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class OpenAiLlmClientTest {
    public static void main(String[] args) throws Exception {
        new OpenAiLlmClientTest().run();
    }

    void run() throws Exception {
        testCallsChatCompletionsAndParses();
        testEndpointOverrideReachesCustomServer();
        System.out.println("OpenAiLlmClientTest: 2 passed");
    }

    void testCallsChatCompletionsAndParses() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> authRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            authRef.set(ex.getRequestHeaders().getFirst("Authorization"));
            bodyRef.set(new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            String resp = """
                {"choices":[{"message":{"content":"hi from gpt"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":4},
                 "model":"gpt-4o-2024-08-06"}
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
            OpenAiLlmClient c = new OpenAiLlmClient("sk-test", base);
            LlmRequest req = new LlmRequest("sys", "hi", 100, 0.5,
                "gpt-4o-2024-08-06");
            LlmResponse resp = c.complete(req);
            assert "hi from gpt".equals(resp.text());
            assert resp.inputTokens() == 12;
            assert "Bearer sk-test".equals(authRef.get());
            assert bodyRef.get().contains("gpt-4o-2024-08-06");
        } finally {
            server.stop(0);
        }
    }

    void testEndpointOverrideReachesCustomServer() throws Exception {
        // Same body shape as OpenAI — proves a local OpenAI-compatible server works.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            String resp = """
                {"choices":[{"message":{"content":"local"}}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            // Empty key should be allowed for local LLM.
            OpenAiLlmClient c = new OpenAiLlmClient("", base);
            LlmResponse resp = c.complete(new LlmRequest(
                "", "ping", 50, 0.7, "llama3.1:70b"));
            assert "local".equals(resp.text());
        } finally {
            server.stop(0);
        }
    }
}
