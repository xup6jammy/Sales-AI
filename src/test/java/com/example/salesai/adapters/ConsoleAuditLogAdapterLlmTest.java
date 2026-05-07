package com.example.salesai.adapters;

import com.example.salesai.audit.LlmCallAuditEntry;

import java.time.Instant;

public final class ConsoleAuditLogAdapterLlmTest {
    public static void main(String[] args) {
        new ConsoleAuditLogAdapterLlmTest().run();
    }

    void run() {
        testFormatsLlmCallEntryIntoSingleLine();
        System.out.println("ConsoleAuditLogAdapterLlmTest: 1 passed");
    }

    void testFormatsLlmCallEntryIntoSingleLine() {
        ConsoleAuditLogAdapter a = new ConsoleAuditLogAdapter(false);
        a.log(new LlmCallAuditEntry(
            Instant.parse("2026-05-07T14:32:18Z"),
            "anthropic", "claude-3-5-sonnet-20241022", "deadbeef",
            2103, 487, 523, 128, 1840L, 0.00385,
            "sk-ant-...8a2f", "draft_reply", "req_a4f1"));
        String s = a.entries().get(0);
        assert s.contains("LLM_CALL");
        assert s.contains("anthropic");
        assert s.contains("claude-3-5-sonnet-20241022");
        assert s.contains("523");
        assert s.contains("128");
        assert s.contains("$0.0039") || s.contains("0.0039");
        assert s.contains("req_a4f1");
        assert !s.contains("sk-ant-api");  // raw key MUST NOT appear
    }
}
