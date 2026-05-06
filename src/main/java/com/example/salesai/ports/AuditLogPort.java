package com.example.salesai.ports;

import java.util.List;

/**
 * Port for emitting audit events. Adapters typically print to stdout
 * AND retain entries so the renderer can show the audit summary.
 */
public interface AuditLogPort {

    void log(String event, String detail);

    /** Returns the in-memory log of events captured so far. */
    List<String> entries();
}
