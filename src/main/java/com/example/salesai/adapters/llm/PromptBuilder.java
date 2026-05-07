package com.example.salesai.adapters.llm;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.RiskAssessment;

public final class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
        You are a senior B2B account manager drafting reply emails on
        behalf of a colleague. You are NOT the customer-facing voice —
        a human will read your drafts and choose one before any send.

        Hard rules:
          - Never auto-send. You only draft.
          - Never promise refunds, contract changes, legal commitments,
            or special discounts. Those require manager approval and
            are blocked upstream.
          - Match the customer's tone but stay professional.
          - Reply in the same language the customer wrote in.

        Output strict JSON in this exact shape:
          {"drafts": [
            {"strategy": "formal_safe",
             "subject": "...",
             "body": "..."},
            {"strategy": "warm_relationship",
             "subject": "...",
             "body": "..."}
          ]}

        Both drafts MUST address the customer's question concretely.
        Do not include any text outside the JSON.
        """;

    private PromptBuilder() {}

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userPrompt(
            CustomerProfile profile,
            EmailThread thread,
            BusinessIntent intent,
            RiskAssessment risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Customer ===\n");
        sb.append("Name: ").append(profile.displayName()).append('\n');
        sb.append("Company: ").append(profile.company()).append('\n');
        sb.append("Tier: ").append(profile.tier()).append('\n');
        sb.append("Account manager: ").append(profile.accountManager()).append('\n');

        sb.append("\n=== Detected ===\n");
        sb.append("Intent: ").append(intent.name()).append('\n');
        sb.append("Risk level: ").append(risk.level().name()).append('\n');
        sb.append("Risk reasons: ").append(String.join("; ", risk.reasons())).append('\n');

        sb.append("\n=== Recent thread (subject: ").append(thread.subject()).append(") ===\n");
        for (EmailMessage m : thread.messages()) {
            sb.append("---\n");
            sb.append("From: ").append(m.from()).append('\n');
            sb.append("To: ").append(String.join(", ", m.to())).append('\n');
            sb.append("Sent: ").append(m.sentAt()).append('\n');
            sb.append("Direction: ").append(m.direction()).append('\n');
            sb.append('\n').append(m.body()).append('\n');
        }

        sb.append("\n=== Task ===\n");
        sb.append("Draft 2 reply options for the human reviewer to choose from.\n");
        return sb.toString();
    }
}
