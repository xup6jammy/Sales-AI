package com.example.salesadvisor.adapters;

import com.example.salesadvisor.domain.CommercialHistory;
import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.ports.CustomerContextPort;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Customer context adapter that reads a single profile from a JSON file
 * on disk. The profile is loaded once at construction time. {@code findByEmail}
 * matches case-insensitively against {@code primaryEmail}.
 */
public final class MockCustomerContextAdapter implements CustomerContextPort {

    private final CustomerProfile profile;

    public MockCustomerContextAdapter(Path jsonPath) {
        this.profile = loadProfile(jsonPath);
    }

    private static CustomerProfile loadProfile(Path jsonPath) {
        String text;
        try {
            text = Files.readString(jsonPath, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new UncheckedIOException(
                    "Failed to read customer profile JSON at " + jsonPath, ioe);
        }
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(text));
        Map<String, Object> historyRaw = MiniJson.asObject(root.get("history"));

        List<CommercialHistory.RecentOrder> orders = new ArrayList<>();
        for (Map<String, Object> o : MiniJson.asObjectList(historyRaw.get("recentOrders"))) {
            orders.add(new CommercialHistory.RecentOrder(
                    MiniJson.asString(o.get("orderId")),
                    MiniJson.asString(o.get("orderedOn")),
                    MiniJson.asLong(o.get("amountUsd")),
                    MiniJson.asString(o.get("status")),
                    MiniJson.asString(o.get("note"))
            ));
        }

        List<CommercialHistory.SupportTicket> tickets = new ArrayList<>();
        for (Map<String, Object> t : MiniJson.asObjectList(historyRaw.get("openSupportTickets"))) {
            tickets.add(new CommercialHistory.SupportTicket(
                    MiniJson.asString(t.get("ticketId")),
                    MiniJson.asString(t.get("openedOn")),
                    MiniJson.asString(t.get("priority")),
                    MiniJson.asString(t.get("summary"))
            ));
        }

        List<String> notes = MiniJson.asStringList(historyRaw.get("notes"));

        CommercialHistory history = new CommercialHistory(orders, tickets, notes);

        return new CustomerProfile(
                MiniJson.asString(root.get("id")),
                MiniJson.asString(root.get("primaryEmail")),
                MiniJson.asString(root.get("displayName")),
                MiniJson.asString(root.get("company")),
                MiniJson.asString(root.get("tier")),
                MiniJson.asString(root.get("industry")),
                MiniJson.asString(root.get("country")),
                MiniJson.asString(root.get("preferredLanguage")),
                MiniJson.asString(root.get("accountManager")),
                MiniJson.asString(root.get("contractStatus")),
                MiniJson.asString(root.get("contractRenewalDate")),
                MiniJson.asString(root.get("paymentStatus")),
                MiniJson.asLong(root.get("lifetimeValueUsd")),
                history
        );
    }

    /** The email of the single profile loaded from JSON, if any. */
    public Optional<String> defaultEmail() {
        return Optional.ofNullable(profile)
                .map(CustomerProfile::primaryEmail);
    }

    @Override
    public Optional<CustomerProfile> findByEmail(String email) {
        if (email == null || profile == null || profile.primaryEmail() == null) {
            return Optional.empty();
        }
        if (profile.primaryEmail().equalsIgnoreCase(email)) {
            return Optional.of(profile);
        }
        return Optional.empty();
    }
}
