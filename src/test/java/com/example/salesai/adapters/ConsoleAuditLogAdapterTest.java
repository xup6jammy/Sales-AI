package com.example.salesai.adapters;

import com.example.salesai.audit.TextAuditEntry;

import java.time.Instant;

public final class ConsoleAuditLogAdapterTest {
    public static void main(String[] args) {
        new ConsoleAuditLogAdapterTest().run();
    }

    void run() {
        testLegacyStringLogStillWorks();
        testStructuredLogAppendsEntry();
        System.out.println("ConsoleAuditLogAdapterTest: 2 passed");
    }

    void testLegacyStringLogStillWorks() {
        ConsoleAuditLogAdapter adapter = new ConsoleAuditLogAdapter();
        adapter.log("STEP", "value");
        assert adapter.entries().size() == 1;
        assert adapter.entries().get(0).contains("STEP");
        assert adapter.entries().get(0).contains("value");
    }

    void testStructuredLogAppendsEntry() {
        ConsoleAuditLogAdapter adapter = new ConsoleAuditLogAdapter();
        adapter.log(new TextAuditEntry(Instant.now(), "MCP_CONNECT", "gmail"));
        assert adapter.entries().size() == 1;
        String line = adapter.entries().get(0);
        assert line.contains("MCP_CONNECT gmail")
            : "expected formatted 'MCP_CONNECT gmail', got: " + line;
        assert !line.contains("TextAuditEntry[")
            : "raw record toString must not appear in audit output, got: " + line;
    }
}
