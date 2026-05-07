package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeminiLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-1.5-pro-002";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public GeminiLlmClient(String apiKey) { this(apiKey, DEFAULT_BASE); }

    public GeminiLlmClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY missing");
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
        if (!req.systemPrompt().isEmpty()) {
            body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", req.systemPrompt()))));
        }
        body.put("contents", List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", req.userPrompt())))));
        body.put("generationConfig", Map.of(
            "maxOutputTokens", req.maxTokens(),
            "temperature", req.temperature()));

        String json = MiniJson.write(body);
        String url = baseUrl + "/v1beta/models/" + req.model()
            + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(url))
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
            throw new IOException("gemini " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> candidates = (List<?>) root.getOrDefault("candidates", List.of());
        StringBuilder text = new StringBuilder();
        if (!candidates.isEmpty()) {
            Map<String, Object> first = MiniJson.asObject(candidates.get(0));
            Map<String, Object> content = MiniJson.asObject(
                first.getOrDefault("content", Map.of()));
            List<?> parts = (List<?>) content.getOrDefault("parts", List.of());
            for (Object p : parts) {
                Map<String, Object> pm = MiniJson.asObject(p);
                if (text.length() > 0) text.append('\n');
                text.append(MiniJson.asString(pm.getOrDefault("text", "")));
            }
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usageMetadata", Map.of()));
        int inTokens = (int) MiniJson.asLong(usage.get("promptTokenCount"));
        int outTokens = (int) MiniJson.asLong(usage.get("candidatesTokenCount"));
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text.toString(), inTokens, outTokens, req.model(), latency);
    }

    @Override
    public String providerName() { return "gemini"; }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
