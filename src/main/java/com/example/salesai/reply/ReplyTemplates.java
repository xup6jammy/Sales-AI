package com.example.salesai.reply;

import com.example.salesai.domain.CustomerProfile;

/**
 * Tiny library of bilingual phrasings used by {@link ReplyComposer}.
 *
 * <p>Each helper returns a single line. The composer is responsible
 * for assembling them in the right order. Keeping the building blocks
 * separate makes it easy to adjust tone without changing structure.
 */
public final class ReplyTemplates {

    private ReplyTemplates() {}

    /** "Re: ..." prefix unless the subject already has one. */
    public static String replySubject(String original, String suffix) {
        String base = original == null ? "(no subject)" : original.trim();
        if (!base.toLowerCase().startsWith("re:")) {
            base = "Re: " + base;
        }
        if (suffix != null && !suffix.isBlank()) {
            base = base + " — " + suffix;
        }
        return base;
    }

    public static String formalOpening(CustomerProfile customer) {
        String name = displayName(customer);
        return "Dear " + name + ",";
    }

    public static String warmOpening(CustomerProfile customer) {
        String name = displayName(customer);
        return "Hi " + name + ",";
    }

    public static String acknowledgement(boolean formal) {
        return formal
                ? "Thank you for the directness of your message. I take the points you raised seriously, and I want to address them in order."
                : "Thanks for being so direct with me — I'd much rather hear it this way than find out later. Let me address each point.";
    }

    public static String empathyLine(boolean formal) {
        return formal
                ? "I understand the impact this has had on your operations and on your team's confidence in us."
                : "I know this hasn't been the experience you expected from us, and I'm not going to pretend otherwise.";
    }

    /** Used when risk has blocked refund / contract / legal commitments. */
    public static String escalationLine(String accountManager, boolean formal) {
        String mgr = (accountManager == null || accountManager.isBlank())
                ? "your account manager" : accountManager;
        return formal
                ? "Because some of the items you raised — in particular any commercial concession — fall outside what I can confirm in writing today, I am bringing them to "
                        + mgr + "'s attention so we can come back to you with a single, signed-off response."
                : "On the commercial side (anything that looks like a refund, credit, or change to the contract), I want to be honest: I won't commit to a number in this email until "
                        + mgr + " has signed it off — that's how we keep our promises clean.";
    }

    public static String commitmentBullet(String commitment) {
        return "  - " + commitment;
    }

    public static String avoidanceNote(boolean formal) {
        return formal
                ? "Please consider this message a status update rather than a final commercial response."
                : "Treat this as me keeping you in the loop, not as the final word on the commercial side.";
    }

    public static String closingFormal(String accountManager) {
        String mgr = (accountManager == null || accountManager.isBlank())
                ? "Account Management" : accountManager;
        return "Best regards,\n" + mgr;
    }

    public static String closingWarm(String accountManager) {
        String mgr = (accountManager == null || accountManager.isBlank())
                ? "your account team" : accountManager;
        return "Talk soon,\n" + mgr;
    }

    private static String displayName(CustomerProfile customer) {
        if (customer == null) {
            return "there";
        }
        if (customer.displayName() != null && !customer.displayName().isBlank()) {
            return customer.displayName();
        }
        return "there";
    }
}
