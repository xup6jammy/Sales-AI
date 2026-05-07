package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnthropicLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public AnthropicLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE);
    }

    public AnthropicLlmClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY missing");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws IOException {
        long start = System.currentTimeMillis();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("max_tokens", req.maxTokens());
        body.put("temperature", req.temperature());
        if (!req.systemPrompt().isEmpty()) body.put("system", req.systemPrompt());
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", req.userPrompt())));

        String json = MiniJson.write(body);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("anthropic " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> content = (List<?>) root.getOrDefault("content", List.of());
        StringBuilder text = new StringBuilder();
        for (Object c : content) {
            Map<String, Object> cm = MiniJson.asObject(c);
            if ("text".equals(cm.get("type"))) {
                if (text.length() > 0) text.append('\n');
                text.append(MiniJson.asString(cm.get("text")));
            }
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usage", Map.of()));
        int inTokens = ((Number) usage.getOrDefault("input_tokens", 0)).intValue();
        int outTokens = ((Number) usage.getOrDefault("output_tokens", 0)).intValue();
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text.toString(), inTokens, outTokens,
            req.model(), latency);
    }

    @Override
    public String providerName() { return "anthropic"; }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
