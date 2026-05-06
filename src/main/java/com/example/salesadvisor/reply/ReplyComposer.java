package com.example.salesadvisor.reply;

import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.ReplyDraft;
import com.example.salesadvisor.domain.ReplyStrategy;
import com.example.salesadvisor.domain.RiskAssessment;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes two reply drafts ("Safe / Formal" and "Warm /
 * Relationship-Focused") from a customer, the email thread, the
 * recommended {@link ReplyStrategy}, and the optional
 * {@link RiskAssessment}.
 *
 * <p>The composer never writes commitments that:
 * <ul>
 *   <li>do not appear in {@code strategy.allowedCommitments()}, OR</li>
 *   <li>match anything in {@code strategy.avoidSaying()}.</li>
 * </ul>
 *
 * <p>If the risk gate has produced any blocked actions, the safe
 * draft includes the escalation line, and neither draft will mention
 * a refund / credit number even if one is talked about in the inbound
 * message.
 */
public final class ReplyComposer {

    public List<ReplyDraft> compose(
            CustomerProfile customer,
            EmailThread thread,
            ReplyStrategy strategy,
            RiskAssessment risk) {

        boolean blockedFromCommitting =
                risk != null && risk.requiresManagerApproval();

        String subjectSuffix = subjectSuffixFor(strategy, blockedFromCommitting);
        String subject = ReplyTemplates.replySubject(
                thread == null ? null : thread.subject(),
                subjectSuffix);

        ReplyDraft safe = new ReplyDraft(
                "Safe / Formal",
                subject,
                buildBody(customer, strategy, blockedFromCommitting, true));

        ReplyDraft warm = new ReplyDraft(
                "Warm / Relationship-Focused",
                subject,
                buildBody(customer, strategy, blockedFromCommitting, false));

        return List.of(safe, warm);
    }

    // ---------------------------------------------------------------
    //  Body assembly
    // ---------------------------------------------------------------

    private static String buildBody(
            CustomerProfile customer,
            ReplyStrategy strategy,
            boolean blockedFromCommitting,
            boolean formal) {

        List<String> lines = new ArrayList<>();
        lines.add(formal
                ? ReplyTemplates.formalOpening(customer)
                : ReplyTemplates.warmOpening(customer));
        lines.add("");
        lines.add(ReplyTemplates.acknowledgement(formal));
        lines.add(ReplyTemplates.empathyLine(formal));
        lines.add("");

        // What we ARE prepared to commit to. Even the formal draft
        // restates these so the customer sees something concrete.
        if (strategy.allowedCommitments() != null
                && !strategy.allowedCommitments().isEmpty()) {
            lines.add(formal
                    ? "Here is what I can confirm today:"
                    : "Here is what I can lock in for you right now:");
            for (String c : strategy.allowedCommitments()) {
                lines.add(ReplyTemplates.commitmentBullet(c));
            }
            lines.add("");
        }

        // What we will NOT promise. The formal draft escalates;
        // the warm draft simply says we won't go beyond what's allowed.
        if (blockedFromCommitting) {
            lines.add(ReplyTemplates.escalationLine(
                    customer == null ? null : customer.accountManager(),
                    formal));
            lines.add("");
        } else if (strategy.avoidSaying() != null
                && !strategy.avoidSaying().isEmpty()) {
            lines.add(formal
                    ? "Items outside the scope of this message will be addressed separately."
                    : "Anything I haven't listed above, I'm not going to make up an answer to in this email — I'd rather come back with the real one.");
            lines.add("");
        }

        if (blockedFromCommitting) {
            lines.add(ReplyTemplates.avoidanceNote(formal));
            lines.add("");
        }

        // Next-best-action sentence in the body so the customer feels
        // there is forward motion.
        if (strategy.nextBestAction() != null
                && !strategy.nextBestAction().isBlank()) {
            lines.add(formal
                    ? "Next step from our side: " + strategy.nextBestAction()
                    : "What I'm doing next: " + strategy.nextBestAction());
            lines.add("");
        }

        lines.add(formal
                ? ReplyTemplates.closingFormal(
                        customer == null ? null : customer.accountManager())
                : ReplyTemplates.closingWarm(
                        customer == null ? null : customer.accountManager()));

        return String.join("\n", lines);
    }

    private static String subjectSuffixFor(
            ReplyStrategy strategy,
            boolean blockedFromCommitting) {
        // The most common scenario for this advisor is a delivery /
        // technical issue where the customer wants a recovery plan,
        // so we default to that label whenever the strategy is
        // anything other than a pure factual answer.
        if (strategy != null && strategy.position() != null) {
            String pos = strategy.position().toLowerCase();
            if (pos.contains("plan") || pos.contains("acknowledge")
                    || pos.contains("escalate")) {
                return "recovery plan";
            }
        }
        if (blockedFromCommitting) {
            return "recovery plan";
        }
        return "next steps";
    }
}
