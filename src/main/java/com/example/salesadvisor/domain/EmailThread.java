package com.example.salesadvisor.domain;

import java.util.List;
import java.util.Optional;

/**
 * A conversation between an account manager and a customer.
 * Messages are expected to be in chronological order — earliest first.
 */
public record EmailThread(
        String threadId,
        String subject,
        String customerEmail,
        List<EmailMessage> messages
) {
    /**
     * Most recent inbound message (i.e. the one we'd be replying to).
     */
    public Optional<EmailMessage> lastInbound() {
        if (messages == null) {
            return Optional.empty();
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            EmailMessage m = messages.get(i);
            if (m.isInbound()) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
