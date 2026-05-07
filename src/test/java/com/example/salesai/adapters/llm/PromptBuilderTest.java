package com.example.salesai.adapters.llm;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;

import java.util.List;

public final class PromptBuilderTest {
    public static void main(String[] args) {
        new PromptBuilderTest().run();
    }

    void run() {
        testSystemPromptForbidsAutoSendAndCommitments();
        testUserPromptIncludesCustomerAndThread();
        System.out.println("PromptBuilderTest: 2 passed");
    }

    void testSystemPromptForbidsAutoSendAndCommitments() {
        String sys = PromptBuilder.systemPrompt();
        assert sys.toLowerCase().contains("never auto-send");
        assert sys.toLowerCase().contains("never promise");
        assert sys.contains("strict JSON");
        assert sys.contains("drafts");
    }

    void testUserPromptIncludesCustomerAndThread() {
        CustomerProfile profile = sampleCustomer();
        EmailThread thread = new EmailThread("thr-1", "Order ETA?",
            "alice@acme.com",
            List.of(new EmailMessage(
                "m-1", "alice@acme.com", List.of("support@vendor.com"),
                "2026-05-07T10:30:00Z", "INBOUND", "When does my order ship?")));
        RiskAssessment risk = new RiskAssessment(
            RiskLevel.LOW, List.of("routine inquiry"), false, List.of());

        String prompt = PromptBuilder.userPrompt(
            profile, thread, BusinessIntent.INQUIRY, risk);
        assert prompt.contains("ACME") || prompt.contains("Standard")
            : "expected customer info in prompt: " + prompt;
        assert prompt.contains("Order ETA?") : "expected thread subject";
        assert prompt.contains("INQUIRY") : "expected intent";
        assert prompt.contains("LOW") : "expected risk level";
    }

    private static CustomerProfile sampleCustomer() {
        return new CustomerProfile(
            "C-1", "alice@acme.com", "Alice Adams", "ACME Corp",
            "Standard", "manufacturing", "US", "en",
            "Pat Manager", "active", "2027-01-01", "paid",
            10000L, new CommercialHistory(List.of(), List.of(), List.of()));
    }
}
