package com.example.salesai.adapters.llm;

public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        String model,
        long latencyMs) {}
