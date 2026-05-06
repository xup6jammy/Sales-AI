package com.example.salesai.app;

import com.example.salesai.domain.AdvisorResult;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.FollowUpAction;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;

import java.util.List;

/**
 * Renders the {@link AdvisorResult} into a deterministic, human-readable
 * report. The section order and headings are part of the public contract
 * with the CLI's stdout.
 */
public final class AdvisorReportRenderer {

    public String render(AdvisorResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Sales AI — Report ===\n");
        if (result.draftDeliveryBlocked()) {
            sb.append("!! DRAFTS ARE BLOCKED — manager approval required before this reply can leave the building !!\n");
        }
        sb.append('\n');

        renderCustomerContext(sb, result.customer(), result.history());
        sb.append('\n');
        renderEmailSummary(sb, result.thread(),
                result.intent() == null ? "UNKNOWN" : result.intent().name(),
                result.tone() == null ? "NEUTRAL" : result.tone().name());
        sb.append('\n');
        renderRisk(sb, result.risk());
        sb.append('\n');
        renderStrategy(sb, result.strategy());
        sb.append('\n');
        renderDrafts(sb, result.drafts());
        sb.append('\n');
        renderFollowUps(sb, result.followUps());
        sb.append('\n');
        renderAuditSummary(sb, result.auditTrail());
        sb.append('\n');
        sb.append("=== End of Report ===\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    //  Sections
    // ---------------------------------------------------------------

    private static void renderCustomerContext(
            StringBuilder sb,
            CustomerProfile customer,
            CommercialHistory history) {
        sb.append("Customer Context\n");
        if (customer == null) {
            sb.append("- (customer not found)\n");
            return;
        }
        sb.append("- Name: ").append(safe(customer.displayName())).append('\n');
        sb.append("- Company: ").append(safe(customer.company())).append('\n');
        sb.append("- Tier: ").append(safe(customer.tier())).append('\n');
        sb.append("- Contract status: ").append(safe(customer.contractStatus()));
        if (customer.contractRenewalDate() != null) {
            sb.append(" (renews ").append(customer.contractRenewalDate())
                    .append(')');
        }
        sb.append('\n');
        sb.append("- Payment status: ")
                .append(safe(customer.paymentStatus())).append('\n');
        sb.append("- Recent orders:");
        if (history == null || history.recentOrders() == null
                || history.recentOrders().isEmpty()) {
            sb.append(" (none)\n");
        } else {
            sb.append('\n');
            for (CommercialHistory.RecentOrder o : history.recentOrders()) {
                sb.append("    * ").append(o.orderId())
                        .append(" — ").append(o.orderedOn())
                        .append(" — $").append(o.amountUsd())
                        .append(" — ").append(o.status())
                        .append(" (").append(safe(o.note())).append(")\n");
            }
        }
        sb.append("- Recent support state:");
        if (history == null || history.openSupportTickets() == null
                || history.openSupportTickets().isEmpty()) {
            sb.append(" no open tickets\n");
        } else {
            sb.append('\n');
            for (CommercialHistory.SupportTicket t : history.openSupportTickets()) {
                sb.append("    * ").append(t.ticketId())
                        .append(" [").append(t.priority()).append("]")
                        .append(" since ").append(t.openedOn())
                        .append(": ").append(safe(t.summary()))
                        .append('\n');
            }
        }
    }

    private static void renderEmailSummary(
            StringBuilder sb,
            EmailThread thread,
            String intent,
            String tone) {
        sb.append("Email Summary\n");
        if (thread == null) {
            sb.append("- (no thread loaded)\n");
            return;
        }
        sb.append("- Subject: ").append(safe(thread.subject())).append('\n');
        sb.append("- Current intent: ").append(intent).append('\n');
        sb.append("- Emotional tone: ").append(tone).append('\n');
        sb.append("- Key customer ask: ");
        EmailMessage lastInbound = thread.lastInbound().orElse(null);
        if (lastInbound == null) {
            sb.append("(no inbound message)\n");
        } else {
            sb.append(firstSentence(lastInbound.body())).append('\n');
        }
    }

    private static void renderRisk(StringBuilder sb, RiskAssessment risk) {
        sb.append("Risk Assessment\n");
        if (risk == null) {
            sb.append("- Risk level: UNKNOWN\n");
            sb.append("- Reasons: (none)\n");
            sb.append("- Requires manager approval: NO\n");
            return;
        }
        sb.append("- Risk level: ").append(risk.level().name()).append('\n');
        sb.append("- Reasons:\n");
        for (String r : risk.reasons()) {
            sb.append("    * ").append(r).append('\n');
        }
        sb.append("- Requires manager approval: ")
                .append(risk.requiresManagerApproval() ? "YES" : "NO")
                .append('\n');
    }

    private static void renderStrategy(StringBuilder sb, ReplyStrategy strategy) {
        sb.append("Recommended Reply Strategy\n");
        if (strategy == null) {
            sb.append("- (no strategy — workflow short-circuited)\n");
            return;
        }
        sb.append("- Tone: ").append(safe(strategy.tone())).append('\n');
        sb.append("- Position: ").append(safe(strategy.position())).append('\n');
        sb.append("- Avoid saying:\n");
        for (String a : strategy.avoidSaying()) {
            sb.append("    * ").append(a).append('\n');
        }
        sb.append("- Allowed commitments:\n");
        for (String c : strategy.allowedCommitments()) {
            sb.append("    * ").append(c).append('\n');
        }
        sb.append("- Next best action: ")
                .append(safe(strategy.nextBestAction())).append('\n');
    }

    private static void renderDrafts(StringBuilder sb, List<ReplyDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            sb.append("Draft Option A: Safe / Formal\n");
            sb.append("Subject: (no draft generated)\n");
            sb.append("Body: (no draft generated)\n\n");
            sb.append("Draft Option B: Warm / Relationship-Focused\n");
            sb.append("Subject: (no draft generated)\n");
            sb.append("Body: (no draft generated)\n");
            return;
        }
        ReplyDraft a = findByLabel(drafts, "Safe / Formal");
        ReplyDraft b = findByLabel(drafts, "Warm / Relationship-Focused");
        if (a == null && drafts.size() >= 1) a = drafts.get(0);
        if (b == null && drafts.size() >= 2) b = drafts.get(1);

        sb.append("Draft Option A: Safe / Formal\n");
        if (a == null) {
            sb.append("Subject: (n/a)\n");
            sb.append("Body: (n/a)\n");
        } else {
            sb.append("Subject: ").append(safe(a.subject())).append('\n');
            sb.append("Body:\n").append(safe(a.body())).append('\n');
        }
        sb.append('\n');
        sb.append("Draft Option B: Warm / Relationship-Focused\n");
        if (b == null) {
            sb.append("Subject: (n/a)\n");
            sb.append("Body: (n/a)\n");
        } else {
            sb.append("Subject: ").append(safe(b.subject())).append('\n');
            sb.append("Body:\n").append(safe(b.body())).append('\n');
        }
    }

    private static void renderFollowUps(
            StringBuilder sb, List<FollowUpAction> actions) {
        sb.append("Follow-Up Actions\n");
        if (actions == null || actions.isEmpty()) {
            sb.append("- (none)\n");
            return;
        }
        for (FollowUpAction a : actions) {
            sb.append("- ").append(safe(a.title()))
                    .append(" — owner: ").append(safe(a.owner()))
                    .append(", due: ").append(safe(a.dueBy())).append('\n');
            sb.append("    ").append(safe(a.detail())).append('\n');
        }
    }

    private static void renderAuditSummary(
            StringBuilder sb, List<String> entries) {
        sb.append("Audit Summary\n");
        if (entries == null || entries.isEmpty()) {
            sb.append("- (no audit events)\n");
            return;
        }
        for (String e : entries) {
            sb.append("- ").append(e).append('\n');
        }
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static ReplyDraft findByLabel(List<ReplyDraft> drafts, String label) {
        for (ReplyDraft d : drafts) {
            if (label.equals(d.label())) {
                return d;
            }
        }
        return null;
    }

    private static String firstSentence(String body) {
        if (body == null) {
            return "";
        }
        // Trim and collapse newlines so the report stays on one line.
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 240) {
            return compact.substring(0, 240) + "...";
        }
        return compact;
    }
}
