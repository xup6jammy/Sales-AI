package com.example.salesai.adapters.llm;

import java.io.IOException;

public interface LlmClient {

    /** Calls the underlying LLM and returns a parsed response. */
    LlmResponse complete(LlmRequest request) throws IOException;

    /** Stable provider name for audit (e.g., "anthropic", "openai", "gemini"). */
    String providerName();

    /** Provider-specific default model when --llm-model is not passed. */
    String defaultModel();
}
