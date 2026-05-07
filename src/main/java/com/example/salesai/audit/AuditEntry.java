package com.example.salesai.audit;

import java.time.Instant;

/**
 * Sealed root of all structured audit entries written by the engine.
 *
 * <p>The {@code TextAuditEntry} variant preserves the legacy
 * {@code event/detail} string-pair shape used by the existing workflow.
 * Phase 4 will add {@code LlmCallAuditEntry} as a second permitted variant
 * carrying structured LLM-call telemetry for SOC2 / ISO 27001 traceability.
 */
public sealed interface AuditEntry permits TextAuditEntry {

    Instant timestamp();
}
