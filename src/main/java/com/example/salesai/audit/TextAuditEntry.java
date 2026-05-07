package com.example.salesai.audit;

import java.time.Instant;

public record TextAuditEntry(
        Instant timestamp,
        String event,
        String detail) implements AuditEntry {

    public TextAuditEntry {
        if (timestamp == null) throw new IllegalArgumentException("timestamp");
        if (event == null || event.isBlank()) throw new IllegalArgumentException("event");
        if (detail == null) detail = "";
    }
}
