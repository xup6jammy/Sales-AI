package com.example.salesai.adapters;

import com.example.salesai.ports.AuditLogPort;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Audit log adapter that prints each event to stdout AND keeps a copy
 * in memory so the renderer can show an audit summary at the bottom
 * of the report.
 *
 * <p>Format: {@code [yyyy-MM-ddTHH:mm:ssZ] EVENT: detail}
 */
public final class ConsoleAuditLogAdapter implements AuditLogPort {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final List<String> entries = new ArrayList<>();
    private final boolean printToStdout;

    public ConsoleAuditLogAdapter() {
        this(true);
    }

    public ConsoleAuditLogAdapter(boolean printToStdout) {
        this.printToStdout = printToStdout;
    }

    @Override
    public void log(String event, String detail) {
        String stamp = FMT.format(Instant.now());
        String line = "[" + stamp + "] " + event
                + (detail == null || detail.isEmpty() ? "" : ": " + detail);
        entries.add(line);
        if (printToStdout) {
            System.out.println(line);
        }
    }

    @Override
    public List<String> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public void log(com.example.salesai.audit.AuditEntry entry) {
        String formatted = formatEntry(entry);
        log(formatted, "");
    }

    private static String formatEntry(com.example.salesai.audit.AuditEntry entry) {
        if (entry instanceof com.example.salesai.audit.TextAuditEntry t) {
            return t.event() + " " + t.detail();
        }
        // Phase 4 will add LlmCallAuditEntry formatting here in Task 4.9.
        return entry.toString();
    }
}
