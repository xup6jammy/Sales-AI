package com.example.salesadvisor.domain;

import java.util.List;

/**
 * The slice of CRM data the advisor cares about: recent orders,
 * open support tickets, and free-form notes from the account team.
 */
public record CommercialHistory(
        List<RecentOrder> recentOrders,
        List<SupportTicket> openSupportTickets,
        List<String> notes
) {

    /** A condensed view of a recent order — enough to mention in a reply. */
    public record RecentOrder(
            String orderId,
            String orderedOn,
            long amountUsd,
            String status,
            String note
    ) {}

    /** Open ticket — surfaces things like firmware bugs or hardware issues. */
    public record SupportTicket(
            String ticketId,
            String openedOn,
            String priority,
            String summary
    ) {}
}
