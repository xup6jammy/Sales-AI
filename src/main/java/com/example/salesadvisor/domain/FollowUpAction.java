package com.example.salesadvisor.domain;

/**
 * A concrete next step the account team should take outside of the
 * email reply itself (e.g. open a logistics ticket, schedule a call).
 */
public record FollowUpAction(
        String title,
        String owner,
        String dueBy,
        String detail
) {}
