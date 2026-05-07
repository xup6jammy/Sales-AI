package com.example.salesai.adapters.llm;

import com.example.salesai.audit.AuditEntry;

import java.time.Instant;

public final class LlmCallAuditEntryTest {
    public static void main(String[] args) {
        new LlmCallAuditEntryTest().run();
    }

    void run() {
        testRecordHoldsAllFields();
        testIsAuditEntry();
        testApiKeyFingerprintHidesMostOfTheKey();
        System.out.println("LlmCallAuditEntryTest: 3 passed");
    }

    void testRecordHoldsAllFields() {
        LlmCallAuditEntry e = sample();
        assert "anthropic".equals(e.provider());
        assert "claude-3-5-sonnet-20241022".equals(e.model());
        assert e.inputTokens() == 523;
        assert e.outputTokens() == 128;
        assert e.latencyMs() == 1840L;
    }

    void testIsAuditEntry() {
        AuditEntry e = sample();
        assert e instanceof LlmCallAuditEntry;
    }

    void testApiKeyFingerprintHidesMostOfTheKey() {
        String fp = LlmCallAuditEntry.fingerprint("sk-ant-api03-XXXXXXXXabcd");
        assert fp.startsWith("sk-ant-");
        assert fp.endsWith("abcd");
        assert !fp.contains("XXXXX");
    }

    private static LlmCallAuditEntry sample() {
        return new LlmCallAuditEntry(
            Instant.parse("2026-05-07T14:32:18Z"),
            "anthropic",
            "claude-3-5-sonnet-20241022",
            "deadbeef",
            2103,
            487,
            523,
            128,
            1840L,
            0.00385,
            "sk-ant-...8a2f",
            "draft_reply",
            "req_a4f1");
    }
}
