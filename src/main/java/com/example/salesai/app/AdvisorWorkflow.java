package com.example.salesai.app;

import com.example.salesai.adapters.TemplateReplyDraftAdapter;
import com.example.salesai.domain.AdvisorRequest;
import com.example.salesai.domain.AdvisorResult;
import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.EmotionalTone;
import com.example.salesai.domain.FollowUpAction;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;
import com.example.salesai.classify.RuleBasedIntentClassifier;
import com.example.salesai.ports.ApprovalPort;
import com.example.salesai.ports.AuditLogPort;
import com.example.salesai.ports.CrmPort;
import com.example.salesai.ports.CustomerContextPort;
import com.example.salesai.ports.EmailThreadPort;
import com.example.salesai.ports.ReplyDraftPort;
import com.example.salesai.ports.RiskPolicyPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level orchestrator. Wires the ports together and produces a
 * fully-populated {@link AdvisorResult}.
 *
 * <p>The workflow is intentionally linear so the audit trail is easy
 * to follow: each step either succeeds and is logged, or short-circuits
 * with a structured "we couldn't help" result that the renderer can
 * still print.
 */
public final class AdvisorWorkflow {

    private final CustomerContextPort customerContext;
    private final EmailThreadPort emailThread;
    private final RiskPolicyPort riskPolicy;
    private final ReplyDraftPort replyDraft;
    private final CrmPort crm;
    private final ApprovalPort approval;
    private final AuditLogPort audit;
    private final RuleBasedIntentClassifier classifier;

    public AdvisorWorkflow(
            CustomerContextPort customerContext,
            EmailThreadPort emailThread,
            RiskPolicyPort riskPolicy,
            ReplyDraftPort replyDraft,
            CrmPort crm,
            ApprovalPort approval,
            AuditLogPort audit) {
        this.customerContext = customerContext;
        this.emailThread = emailThread;
        this.riskPolicy = riskPolicy;
        this.replyDraft = replyDraft;
        this.crm = crm;
        this.approval = approval;
        this.audit = audit;
        this.classifier = new RuleBasedIntentClassifier();
    }

    public AdvisorResult run(AdvisorRequest request) {
        // 1. Lookup customer.
        audit.log("LOOKUP_CUSTOMER", "email=" + request.customerEmail());
        CustomerProfile customer = customerContext
                .findByEmail(request.customerEmail())
                .orElse(null);
        if (customer == null) {
            audit.log("CUSTOMER_NOT_FOUND",
                    "email=" + request.customerEmail());
            return new AdvisorResult(
                    null, null, null,
                    BusinessIntent.UNKNOWN, EmotionalTone.NEUTRAL,
                    null, null,
                    List.of(), List.of(),
                    true, false,
                    new ArrayList<>(audit.entries())
            );
        }

        // 2. Load thread.
        audit.log("LOAD_THREAD",
                "customerEmail=" + customer.primaryEmail());
        EmailThread thread = emailThread
                .loadLatestForCustomer(customer.primaryEmail())
                .orElse(null);
        if (thread == null) {
            audit.log("THREAD_NOT_FOUND",
                    "customerEmail=" + customer.primaryEmail());
            return new AdvisorResult(
                    customer, customer.history(), null,
                    BusinessIntent.UNKNOWN, EmotionalTone.NEUTRAL,
                    null, null,
                    List.of(), List.of(),
                    true, false,
                    new ArrayList<>(audit.entries())
            );
        }

        // 3. Classify intent.
        audit.log("CLASSIFY_INTENT", "thread=" + thread.threadId());
        BusinessIntent intent = classifier.classifyIntent(thread);
        audit.log("INTENT_CLASSIFIED", intent.name());

        // 4. Classify tone.
        audit.log("CLASSIFY_TONE", "thread=" + thread.threadId());
        EmotionalTone tone = classifier.classifyTone(thread);
        audit.log("TONE_CLASSIFIED", tone.name());

        // 5. Evaluate risk.
        audit.log("EVALUATE_RISK", "intent=" + intent + " tone=" + tone);
        RiskAssessment risk = riskPolicy.evaluate(customer, thread, intent, tone);
        audit.log("RISK_LEVEL", risk.level().name()
                + " requiresManagerApproval=" + risk.requiresManagerApproval());

        // 6. Decide strategy.
        audit.log("DECIDE_STRATEGY",
                "level=" + risk.level());
        ReplyStrategy strategy = deriveStrategy(customer, intent, tone, risk);

        // 6b. Hand the risk hint to the draft adapter, if it can use it.
        if (replyDraft instanceof TemplateReplyDraftAdapter t) {
            t.setRiskAssessment(risk);
        }

        // 7. Risk gate — THE INVARIANT.
        //    The LLM (or any other reply draft adapter) is NEVER invoked when
        //    the risk level blocks auto-drafting. This is enforced by code
        //    structure, not convention. See AdvisorWorkflowRiskGateTest.
        //
        //    Manifesto: "manager-approval gate 不會消失,即使 autonomy 越來越高"
        //    — the manager-approval gate doesn't disappear, no matter how much
        //    autonomy increases. AI handles volume; humans handle nuance.
        List<ReplyDraft> drafts;
        if (risk.level().blocksAutoDraft()) {
            audit.log("DRAFTS_BLOCKED_BY_RISK_GATE",
                "level=" + risk.level()
                + " requiresManagerApproval=" + risk.requiresManagerApproval()
                + " reasons=" + String.join("; ", risk.reasons())
                + " (LLM not invoked; data did not leave perimeter)");
            drafts = List.of(
                new ReplyDraft(
                    "Safe / Formal",
                    "[manager approval required]",
                    "Drafts blocked by risk gate. Reasons: "
                        + String.join("; ", risk.reasons())
                        + ". Route to " + customer.accountManager()
                        + " before any reply leaves the building."),
                new ReplyDraft(
                    "Warm / Relationship-Focused",
                    "[manager approval required]",
                    "(no draft — see Safe/Formal option for the block reason)"));
        } else {
            audit.log("GENERATE_DRAFTS", "tone=" + strategy.tone());
            drafts = replyDraft.generate(customer, thread, strategy);
        }

        // 8. Recommend follow-ups.
        audit.log("RECOMMEND_FOLLOWUPS", "intent=" + intent);
        List<FollowUpAction> followUps = recommendFollowUps(customer, intent, risk);

        // 9. Approval gate.
        audit.log("EVALUATE_APPROVAL",
                "requiresManagerApproval=" + risk.requiresManagerApproval());
        boolean approved = approval.isApproved(risk, request);
        // Delivery is blocked when either (a) approval was denied, OR
        // (b) the risk gate fired — auto-draft is not permitted at this
        // risk level, so the synthesised placeholder drafts must NOT be
        // sent without explicit manager handling.
        boolean blocked = !approved || risk.level().blocksAutoDraft();

        // 10. CRM record (for the lookup, NOT for any unsent draft).
        crm.recordInteraction(customer.id(),
                "Advisor reviewed thread " + thread.threadId()
                        + "; intent=" + intent + "; risk=" + risk.level()
                        + "; drafts " + (blocked ? "BLOCKED" : "READY"));

        return new AdvisorResult(
                customer,
                customer.history(),
                thread,
                intent,
                tone,
                risk,
                strategy,
                drafts,
                followUps,
                blocked,
                approved && risk.requiresManagerApproval(),
                new ArrayList<>(audit.entries())
        );
    }

    // ---------------------------------------------------------------
    //  Strategy derivation
    // ---------------------------------------------------------------

    private static ReplyStrategy deriveStrategy(
            CustomerProfile customer,
            BusinessIntent intent,
            EmotionalTone tone,
            RiskAssessment risk) {

        String accountManager = customer == null ? "the account manager"
                : customer.accountManager();

        // Avoid-saying = whatever risk has blocked.
        List<String> avoid = new ArrayList<>(risk.blockedActions());

        List<String> commitments = new ArrayList<>();
        commitments.add("Acknowledge receipt and the urgency of the situation today");
        commitments.add("Pull together logistics, engineering, and account management within 24 hours");
        commitments.add("Provide a written status update with concrete dates by end of next business day");

        String tonePhrase;
        String position;
        String nextBestAction;

        switch (risk.level()) {
            case REQUIRES_MANAGER_APPROVAL -> {
                tonePhrase = "formal, careful, no commitments";
                position = "acknowledge, no commitments yet, escalate";
                nextBestAction = "Hand off to " + accountManager
                        + " with full context; do not reply until approved";
            }
            case HIGH -> {
                tonePhrase = "formal";
                position = "acknowledge, no commitments yet, escalate";
                nextBestAction = "Confirm receipt and route the commercial asks to "
                        + accountManager + " for sign-off before replying.";
            }
            case MEDIUM -> {
                tonePhrase = "empathetic, professional";
                position = "acknowledge, propose plan with conservative commitments";
                nextBestAction = "Send the conservative recovery plan and "
                        + "schedule a 24-hour check-in with the customer.";
            }
            case LOW -> {
                tonePhrase = "warm, conversational";
                position = "answer directly, propose next step";
                nextBestAction = "Reply directly with the answer and propose a next step.";
            }
            default -> {
                tonePhrase = "neutral";
                position = "answer directly";
                nextBestAction = "Reply with the most relevant answer.";
            }
        }

        // Tone tweaks based on emotion of the inbound message.
        if (tone == EmotionalTone.ANGRY || tone == EmotionalTone.FRUSTRATED) {
            tonePhrase = tonePhrase + " (the customer is "
                    + tone.name().toLowerCase() + " — lead with empathy)";
        }
        if (tone == EmotionalTone.URGENT) {
            tonePhrase = tonePhrase + " (the customer is signalling urgency — "
                    + "acknowledge time pressure explicitly)";
        }

        return new ReplyStrategy(tonePhrase, position, avoid, commitments,
                nextBestAction);
    }

    // ---------------------------------------------------------------
    //  Follow-ups
    // ---------------------------------------------------------------

    private static List<FollowUpAction> recommendFollowUps(
            CustomerProfile customer,
            BusinessIntent intent,
            RiskAssessment risk) {
        List<FollowUpAction> out = new ArrayList<>();
        String owner = customer == null ? "Account Manager" : customer.accountManager();

        if (risk.requiresManagerApproval()) {
            out.add(new FollowUpAction(
                    "Brief the manager",
                    owner,
                    "today",
                    "Walk the manager through the inbound message, the risk reasons, and the proposed reply before any draft leaves the building."
            ));
        }
        if (intent == BusinessIntent.DELIVERY_DELAY
                || intent == BusinessIntent.COMPLAINT
                || intent == BusinessIntent.CHURN_RISK) {
            out.add(new FollowUpAction(
                    "Logistics root-cause review",
                    "Logistics lead",
                    "within 24 hours",
                    "Get a written explanation of why the latest order missed its ETA, plus a revised delivery date."
            ));
        }
        if (intent == BusinessIntent.TECHNICAL_SUPPORT
                || hasOpenTechnicalTicket(customer)) {
            out.add(new FollowUpAction(
                    "Engineering update on open ticket",
                    "Engineering lead",
                    "within 48 hours",
                    "Confirm a firmware fix ETA for the open HIGH-priority ticket and write it up in customer-friendly language."
            ));
        }
        if (customer != null && customer.isVip()) {
            out.add(new FollowUpAction(
                    "Schedule executive check-in",
                    owner,
                    "this week",
                    "VIP customer — set up a short call with our account exec to keep the relationship anchored."
            ));
        }
        if (out.isEmpty()) {
            out.add(new FollowUpAction(
                    "Standard reply",
                    owner,
                    "today",
                    "No special handling required; reply per normal SLA."
            ));
        }
        return out;
    }

    private static boolean hasOpenTechnicalTicket(CustomerProfile customer) {
        if (customer == null || customer.history() == null
                || customer.history().openSupportTickets() == null) {
            return false;
        }
        for (CommercialHistory.SupportTicket t
                : customer.history().openSupportTickets()) {
            if (t != null) {
                return true;
            }
        }
        return false;
    }

    // Suppress unused warning for RiskLevel import in older javac modes.
    @SuppressWarnings("unused")
    private static RiskLevel sentinel() {
        return RiskLevel.LOW;
    }
}
