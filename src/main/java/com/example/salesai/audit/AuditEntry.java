package com.example.salesai.audit;

import com.example.salesai.adapters.llm.LlmCallAuditEntry;

import java.time.Instant;

/**
 * Sealed root of all structured audit entries written by the engine.
 *
 * <p>{@code TextAuditEntry} preserves the legacy event/detail string-pair
 * shape used by the existing workflow. {@code LlmCallAuditEntry} carries
 * structured LLM-call telemetry for SOC2 / ISO 27001 traceability.
 */
public sealed interface AuditEntry
        permits TextAuditEntry, LlmCallAuditEntry {

    Instant timestamp();
}
