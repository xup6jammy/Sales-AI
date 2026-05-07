package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class AnthropicLlmClientTest {

    public static void main(String[] args) throws Exception {
        new AnthropicLlmClientTest().run();
    }

    void run() throws Exception {
        testBuildsCorrectRequestAndParsesResponse();
        testRaises4xx();
        System.out.println("AnthropicLlmClientTest: 2 passed");
    }

    void testBuildsCorrectRequestAndParsesResponse() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("x-api-key"));
            byte[] body = ex.getRequestBody().readAllBytes();
            capturedBody.set(new String(body, StandardCharsets.UTF_8));
            String resp = """
                {"content":[{"type":"text","text":"hi back"}],
                 "usage":{"input_tokens":10,"output_tokens":3},
                 "model":"claude-3-5-sonnet-20241022"}
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
            AnthropicLlmClient c = new AnthropicLlmClient("sk-ant-test-fake0000abcd", base);
            LlmRequest req = new LlmRequest(
                "system instructions", "say hi",
                100, 0.5, "claude-3-5-sonnet-20241022");
            LlmResponse resp = c.complete(req);
            assert "hi back".equals(resp.text()) : resp.text();
            assert resp.inputTokens() == 10;
            assert resp.outputTokens() == 3;
            assert "sk-ant-test-fake0000abcd".equals(capturedAuth.get());
            assert capturedBody.get().contains("claude-3-5-sonnet-20241022");
            assert capturedBody.get().contains("system instructions");
            assert capturedBody.get().contains("say hi");
        } finally {
            server.stop(0);
        }
    }

    void testRaises4xx() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            String body = "{\"error\":{\"message\":\"invalid api key\"}}";
            ex.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            AnthropicLlmClient c = new AnthropicLlmClient("bad", base);
            LlmRequest req = new LlmRequest(
                "", "hi", 100, 0.5, "claude-3-5-sonnet-20241022");
            try {
                c.complete(req);
                throw new AssertionError("expected IOException");
            } catch (java.io.IOException ok) {
                assert ok.getMessage().contains("401") : ok.getMessage();
            }
        } finally {
            server.stop(0);
        }
    }
}
