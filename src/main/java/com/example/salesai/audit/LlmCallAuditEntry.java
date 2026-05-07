package com.example.salesai.audit;

import java.time.Instant;

public record LlmCallAuditEntry(
        Instant timestamp,
        String provider,
        String model,
        String promptHash,
        int promptLengthChars,
        int responseLengthChars,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        double estimatedCostUsd,
        String apiKeyFingerprint,
        String workflowStep,
        String requestId) implements AuditEntry {

    public LlmCallAuditEntry {
        if (timestamp == null) throw new IllegalArgumentException("timestamp");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model");
    }

    /**
     * Redact an API key for logging. Keeps the provider prefix (everything
     * before the first dash after "sk-") and the last 4 characters; replaces
     * the rest with "...".
     */
    public static String fingerprint(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) return "***";
        int firstDashAfterSk = apiKey.indexOf('-', 3);
        String prefix = firstDashAfterSk > 0
            ? apiKey.substring(0, firstDashAfterSk + 1)
            : apiKey.substring(0, Math.min(7, apiKey.length()));
        String suffix = apiKey.substring(apiKey.length() - 4);
        return prefix + "..." + suffix;
    }
}
