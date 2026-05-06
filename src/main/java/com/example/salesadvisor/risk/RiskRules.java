package com.example.salesadvisor.risk;

import com.example.salesadvisor.domain.BusinessIntent;
import com.example.salesadvisor.domain.CommercialHistory;
import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailMessage;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.EmotionalTone;
import com.example.salesadvisor.domain.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure functions that decide the risk level of replying autonomously.
 *
 * <p>The result is split into three pieces because each of them feeds
 * a different downstream concern:
 * <ul>
 *   <li>{@code level} drives whether drafts can be auto-sent.</li>
 *   <li>{@code reasons} feeds the human-readable report.</li>
 *   <li>{@code blockedActions} feeds the reply composer (so it can
 *       avoid promising things we are not allowed to commit to).</li>
 * </ul>
 */
public final class RiskRules {

    private RiskRules() {}

    /** Bundle of (level, reasons, blockedActions). */
    public record Outcome(
            RiskLevel level,
            List<String> reasons,
            boolean requiresManagerApproval,
            List<String> blockedActions
    ) {}

    public static Outcome evaluate(
            CustomerProfile customer,
            EmailThread thread,
            BusinessIntent intent,
            EmotionalTone tone) {

        List<String> reasons = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        RiskLevel level = RiskLevel.LOW;
        boolean requiresManagerApproval = false;

        String body = concatBodies(thread).toLowerCase(Locale.ROOT);

        // -------- Highest gate: customer is signalling churn. --------
        if (containsAny(body,
                "alternative vendors", "alternative vendor",
                "cancel", "解約", "pause renewal",
                "pause the renewal", "switch provider")) {
            level = RiskLevel.REQUIRES_MANAGER_APPROVAL;
            requiresManagerApproval = true;
            reasons.add("Customer signalled churn risk "
                    + "(mentioned alternative vendors / pause renewal / cancel).");
            blocked.add("Promising any contractual concession without manager approval");
        }

        // -------- HIGH triggers (refund, legal, contract). ----------
        if (containsAny(body, "refund", "退款", "credit", "store credit")) {
            level = bumpAtLeast(level, RiskLevel.HIGH);
            requiresManagerApproval = true;
            reasons.add("Customer asked for refund or credit.");
            blocked.add("Confirming a refund or credit amount in the reply");
        }
        if (containsAny(body, "lawsuit", "legal action", "legal", "法務")) {
            level = bumpAtLeast(level, RiskLevel.HIGH);
            requiresManagerApproval = true;
            reasons.add("Customer mentioned legal / 法務 — escalate to legal.");
            blocked.add("Discussing legal liability or admitting fault in writing");
        }
        if (containsAny(body, "contract amendment", "合約變更", "terminate")) {
            level = bumpAtLeast(level, RiskLevel.HIGH);
            requiresManagerApproval = true;
            reasons.add("Customer asked about contract amendment / termination.");
            blocked.add("Agreeing to contract changes in the reply");
        }
        if (containsAny(body, "exceptional discount", "折扣特批")) {
            level = bumpAtLeast(level, RiskLevel.HIGH);
            requiresManagerApproval = true;
            reasons.add("Exceptional / out-of-policy discount requested.");
            blocked.add("Granting an out-of-policy discount in the reply");
        }

        // -------- MEDIUM triggers ----------
        boolean paymentOverdue = customer != null
                && customer.paymentStatus() != null
                && customer.paymentStatus().toUpperCase(Locale.ROOT).contains("OVERDUE");
        if (paymentOverdue && customer.isVip()) {
            level = bumpAtLeast(level, RiskLevel.MEDIUM);
            reasons.add("VIP customer has an overdue payment ("
                    + customer.paymentStatus() + ").");
        }

        if (intent == BusinessIntent.DELIVERY_DELAY
                && level.ordinal() < RiskLevel.MEDIUM.ordinal()) {
            level = bumpAtLeast(level, RiskLevel.LOW);
            reasons.add("Delivery delay topic — keep commitments conservative.");
        }

        if (customer != null && customer.isVip() && tone == EmotionalTone.ANGRY) {
            level = bumpAtLeast(level, RiskLevel.MEDIUM);
            reasons.add("VIP customer is expressing anger — handle with care.");
        }

        // Open HIGH-priority ticket bumps severity by one notch.
        if (hasOpenHighPriorityTicket(customer)) {
            level = bumpOnce(level);
            reasons.add("Customer has an open HIGH-priority support ticket.");
        }

        if (reasons.isEmpty()) {
            reasons.add("No specific risk triggers fired; default LOW.");
        }
        if (blocked.isEmpty()) {
            blocked.add("None — the strategy may include normal commitments.");
        }
        return new Outcome(level, reasons, requiresManagerApproval, blocked);
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private static String concatBodies(EmailThread thread) {
        if (thread == null || thread.messages() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (thread.subject() != null) {
            sb.append(thread.subject()).append('\n');
        }
        for (EmailMessage m : thread.messages()) {
            if (m.body() != null) {
                sb.append(m.body()).append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static RiskLevel bumpAtLeast(RiskLevel current, RiskLevel floor) {
        return current.ordinal() >= floor.ordinal() ? current : floor;
    }

    private static RiskLevel bumpOnce(RiskLevel current) {
        return switch (current) {
            case LOW -> RiskLevel.MEDIUM;
            case MEDIUM -> RiskLevel.HIGH;
            case HIGH -> RiskLevel.REQUIRES_MANAGER_APPROVAL;
            case REQUIRES_MANAGER_APPROVAL -> RiskLevel.REQUIRES_MANAGER_APPROVAL;
        };
    }

    private static boolean hasOpenHighPriorityTicket(CustomerProfile customer) {
        if (customer == null || customer.history() == null) {
            return false;
        }
        List<CommercialHistory.SupportTicket> tickets =
                customer.history().openSupportTickets();
        if (tickets == null) {
            return false;
        }
        for (CommercialHistory.SupportTicket t : tickets) {
            if (t.priority() != null
                    && "HIGH".equalsIgnoreCase(t.priority())) {
                return true;
            }
        }
        return false;
    }
}
