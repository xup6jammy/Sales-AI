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

/**
 * OpenAI Chat Completions client. Also serves any OpenAI-compatible
 * endpoint (Ollama, vLLM, llama.cpp, LM Studio, OpenRouter) by passing a
 * non-default {@code baseUrl} — same wire protocol, different host.
 */
public final class OpenAiLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-2024-08-06";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public OpenAiLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE);
    }

    /** apiKey may be empty for local OpenAI-compatible endpoints. */
    public OpenAiLlmClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl == null ? DEFAULT_BASE : baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws IOException {
        long start = System.currentTimeMillis();

        java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (!req.systemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", req.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", req.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("messages", messages);
        body.put("max_tokens", req.maxTokens());
        body.put("temperature", req.temperature());

        String json = MiniJson.write(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (!apiKey.isEmpty()) {
            b.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("openai " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> choices = (List<?>) root.getOrDefault("choices", List.of());
        String text = "";
        if (!choices.isEmpty()) {
            Map<String, Object> firstChoice = MiniJson.asObject(choices.get(0));
            Map<String, Object> message = MiniJson.asObject(
                firstChoice.getOrDefault("message", Map.of()));
            text = MiniJson.asString(message.getOrDefault("content", ""));
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usage", Map.of()));
        int inTokens = (int) MiniJson.asLong(usage.get("prompt_tokens"));
        int outTokens = (int) MiniJson.asLong(usage.get("completion_tokens"));
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text, inTokens, outTokens, req.model(), latency);
    }

    @Override
    public String providerName() {
        return baseUrl.equals(DEFAULT_BASE) ? "openai" : "openai-compatible";
    }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
