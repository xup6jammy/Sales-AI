package com.example.salesai.ports;

import com.example.salesai.audit.AuditEntry;

import java.util.List;

public interface AuditLogPort {

    /** Legacy two-string call; kept so existing workflow code compiles. */
    void log(String event, String detail);

    /** Structured log call; preferred for new callers (Phase 4). */
    void log(AuditEntry entry);

    /** Returns the in-memory log of events captured so far (formatted strings). */
    List<String> entries();
}
