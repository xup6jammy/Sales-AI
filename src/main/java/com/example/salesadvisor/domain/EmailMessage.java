package com.example.salesadvisor.domain;

import java.util.List;

/**
 * A single message inside an email thread. The {@code direction} field
 * is either {@code "INBOUND"} (from the customer) or {@code "OUTBOUND"}
 * (from the account manager). {@code sentAt} is kept as a string so the
 * advisor doesn't have to commit to a timezone format.
 */
public record EmailMessage(
        String messageId,
        String from,
        List<String> to,
        String sentAt,
        String direction,
        String body
) {
    public boolean isInbound() {
        return "INBOUND".equalsIgnoreCase(direction);
    }
}
