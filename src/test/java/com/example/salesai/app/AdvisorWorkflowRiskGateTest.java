package com.example.salesai.app;

import com.example.salesai.adapters.ConsoleAuditLogAdapter;
import com.example.salesai.adapters.ManualApprovalAdapter;
import com.example.salesai.adapters.NoopCrmAdapter;
import com.example.salesai.domain.AdvisorRequest;
import com.example.salesai.domain.AdvisorResult;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;
import com.example.salesai.ports.CustomerContextPort;
import com.example.salesai.ports.EmailThreadPort;
import com.example.salesai.ports.ReplyDraftPort;
import com.example.salesai.ports.RiskPolicyPort;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdvisorWorkflowRiskGateTest {

    public static void main(String[] args) {
        new AdvisorWorkflowRiskGateTest().run();
    }

    void run() {
        testHighRiskShortCircuitsBeforeReplyDraftPort();
        testRequiresManagerApprovalAlsoBlocks();
        testRequiresManagerApprovalFieldAloneBlocks();
        testLowRiskCallsReplyDraftPort();
        System.out.println("AdvisorWorkflowRiskGateTest: 4 passed");
    }

    /** THE INVARIANT — LLM has no path to HIGH risk customer data. */
    void testHighRiskShortCircuitsBeforeReplyDraftPort() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of();
        };
        AdvisorWorkflow w = workflowWithRisk(RiskLevel.HIGH, false, spy);
        AdvisorResult r = w.run(new AdvisorRequest("alice@acme.com", false));

        assert replyCalls.get() == 0
            : "ReplyDraftPort was called " + replyCalls.get()
              + " times — risk gate FAILED, LLM had path to HIGH risk data!";
        assert r.draftDeliveryBlocked() : "result should be blocked";
    }

    /** REQUIRES_MANAGER_APPROVAL also blocks the LLM. */
    void testRequiresManagerApprovalAlsoBlocks() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of();
        };
        AdvisorWorkflow w = workflowWithRisk(
            RiskLevel.REQUIRES_MANAGER_APPROVAL, true, spy);
        w.run(new AdvisorRequest("alice@acme.com", false));

        assert replyCalls.get() == 0
            : "REQUIRES_MANAGER_APPROVAL did not block — got "
              + replyCalls.get() + " LLM calls";
    }

    /** LOW risk allows the LLM to be called. */
    void testLowRiskCallsReplyDraftPort() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of(
                new ReplyDraft("Safe / Formal", "s", "b"),
                new ReplyDraft("Warm / Relationship-Focused", "s", "b"));
        };
        AdvisorWorkflow w = workflowWithRisk(RiskLevel.LOW, false, spy);
        w.run(new AdvisorRequest("alice@acme.com", false));
        assert replyCalls.get() == 1
            : "ReplyDraftPort should be called exactly once for LOW risk; got "
              + replyCalls.get();
    }

    /**
     * MEDIUM risk + requiresManagerApproval=true — the field alone must
     * still block the LLM. Without this test, Critical #1 would silently
     * regress.
     */
    void testRequiresManagerApprovalFieldAloneBlocks() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of();
        };
        // MEDIUM level (does NOT trigger blocksAutoDraft) but requiresManagerApproval=true
        AdvisorWorkflow w = workflowWithRisk(RiskLevel.MEDIUM, true, spy);
        w.run(new AdvisorRequest("alice@acme.com", false));

        assert replyCalls.get() == 0
            : "MEDIUM+requiresManagerApproval=true did NOT block — got "
              + replyCalls.get() + " LLM calls. SAFETY GAP.";
    }

    /** Build a workflow with stubbed dependencies. */
    private static AdvisorWorkflow workflowWithRisk(
            RiskLevel level, boolean requiresApproval, ReplyDraftPort replyDraft) {
        CustomerContextPort customers = email -> Optional.of(sampleCustomer(email));
        EmailThreadPort threads = email -> Optional.of(sampleThread(email));
        RiskPolicyPort risk = (c, t, i, e) ->
            new RiskAssessment(level,
                List.of("test-reason"), requiresApproval, List.of());
        ConsoleAuditLogAdapter audit = new ConsoleAuditLogAdapter(false);
        return new AdvisorWorkflow(
            customers, threads, risk, replyDraft,
            new NoopCrmAdapter(audit), new ManualApprovalAdapter(audit), audit);
    }

    private static CustomerProfile sampleCustomer(String email) {
        return new CustomerProfile(
            "C-1", email, "Alice", "ACME Corp", "Standard",
            "manufacturing", "US", "en", "Pat Manager",
            "active", "2027-01-01", "paid", 10000L,
            new CommercialHistory(List.of(), List.of(), List.of()));
    }

    private static EmailThread sampleThread(String email) {
        return new EmailThread("t1", "subject", email,
            List.of(new EmailMessage("m-1", email, List.of("us@x.com"),
                "2026-05-07T10:00:00Z", "INBOUND", "body")));
    }
}
