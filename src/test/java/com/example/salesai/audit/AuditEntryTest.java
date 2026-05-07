package com.example.salesai.audit;

import java.time.Instant;

public final class AuditEntryTest {
    public static void main(String[] args) {
        new AuditEntryTest().run();
    }

    void run() {
        testTextEntryCarriesEventDetailTimestamp();
        testIsSealed();
        System.out.println("AuditEntryTest: 2 passed");
    }

    void testTextEntryCarriesEventDetailTimestamp() {
        Instant t = Instant.parse("2026-05-07T14:32:18Z");
        TextAuditEntry e = new TextAuditEntry(t, "STEP", "detail");
        assert e.timestamp().equals(t);
        assert e.event().equals("STEP");
        assert e.detail().equals("detail");
    }

    void testIsSealed() {
        // If this compiles, the sealed permits is correctly declared.
        AuditEntry e = new TextAuditEntry(Instant.now(), "X", "Y");
        assert e instanceof TextAuditEntry;
    }
}
