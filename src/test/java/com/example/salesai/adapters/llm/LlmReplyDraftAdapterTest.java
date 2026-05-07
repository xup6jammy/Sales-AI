package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.ConsoleAuditLogAdapter;
import com.example.salesai.audit.AuditEntry;
import com.example.salesai.audit.LlmCallAuditEntry;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.ports.AuditLogPort;
import com.example.salesai.ports.ReplyDraftPort;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LlmReplyDraftAdapterTest {
    public static void main(String[] args) {
        new LlmReplyDraftAdapterTest().run();
    }

    void run() {
        testProducesTwoDraftsFromValidJsonResponse();
        testWritesAuditEntryAfterEachCall();
        System.out.println("LlmReplyDraftAdapterTest: 2 passed");
    }

    void testProducesTwoDraftsFromValidJsonResponse() {
        FakeLlm llm = new FakeLlm("""
            {"drafts":[
              {"label":"formal_safe","subject":"Order update","body":"Dear..."},
              {"label":"warm_relationship","subject":"Quick update","body":"Hi..."}
            ]}
            """);
        ConsoleAuditLogAdapter audit = new ConsoleAuditLogAdapter(false);
        ReplyDraftPort port = new LlmReplyDraftAdapter(llm, audit, null, "draft_reply");
        List<ReplyDraft> drafts = port.generate(
            sampleCustomer(), sampleThread(), sampleStrategy());
        assert drafts.size() == 2 : "got " + drafts.size();
        // The adapter should canonicalize labels to "Safe / Formal" and
        // "Warm / Relationship-Focused" regardless of what the LLM returned.
        assert "Safe / Formal".equals(drafts.get(0).label())
            : "first draft label: " + drafts.get(0).label();
        assert "Warm / Relationship-Focused".equals(drafts.get(1).label())
            : "second draft label: " + drafts.get(1).label();
    }

    void testWritesAuditEntryAfterEachCall() {
        FakeLlm llm = new FakeLlm("""
            {"drafts":[
              {"label":"formal_safe","subject":"x","body":"y"},
              {"label":"warm_relationship","subject":"x","body":"y"}
            ]}
            """);
        AtomicReference<AuditEntry> captured = new AtomicReference<>();
        AuditLogPort spy = new AuditLogPort() {
            @Override public void log(String e, String d) {}
            @Override public void log(AuditEntry entry) {
                if (entry instanceof LlmCallAuditEntry) captured.set(entry);
            }
            @Override public java.util.List<String> entries() { return List.of(); }
        };
        ReplyDraftPort port = new LlmReplyDraftAdapter(llm, spy, null, "draft_reply");
        port.generate(sampleCustomer(), sampleThread(), sampleStrategy());
        assert captured.get() instanceof LlmCallAuditEntry : "no LlmCallAuditEntry written";
        LlmCallAuditEntry e = (LlmCallAuditEntry) captured.get();
        assert "fake".equals(e.provider());
        assert "fake-model".equals(e.model());
        assert e.inputTokens() == 10;
        assert e.outputTokens() == 20;
    }

    static CustomerProfile sampleCustomer() {
        return new CustomerProfile(
            "C-1", "alice@acme.com", "Alice", "ACME Corp",
            "Standard", "manufacturing", "US", "en",
            "Pat Manager", "active", "2027-01-01", "paid",
            10000L, new CommercialHistory(List.of(), List.of(), List.of()));
    }

    static EmailThread sampleThread() {
        return new EmailThread("t1", "subj", "alice@acme.com",
            List.of(new EmailMessage("m-1", "alice@acme.com",
                List.of("us@x.com"), "2026-05-07T10:00:00Z",
                "INBOUND", "body")));
    }

    static ReplyStrategy sampleStrategy() {
        return new ReplyStrategy("warm", "answer", List.of(), List.of(), "ship");
    }

    static class FakeLlm implements LlmClient {
        final String reply;
        FakeLlm(String reply) { this.reply = reply; }
        @Override public LlmResponse complete(LlmRequest r) throws IOException {
            return new LlmResponse(reply, 10, 20, "fake-model", 50L);
        }
        @Override public String providerName() { return "fake"; }
        @Override public String defaultModel() { return "fake-model"; }
    }
}
