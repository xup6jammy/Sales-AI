package com.example.salesai.adapters.llm;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        int maxTokens,
        double temperature,
        String model) {

    public LlmRequest {
        if (systemPrompt == null) systemPrompt = "";
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt");
        }
        if (maxTokens <= 0) maxTokens = 1024;
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature out of range");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model");
        }
    }
}
