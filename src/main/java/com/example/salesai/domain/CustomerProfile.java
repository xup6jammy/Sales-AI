package com.example.salesai.domain;

/**
 * The full customer profile used by the advisor. Acts as the glue
 * between identity, commercial state, and history.
 */
public record CustomerProfile(
        String id,
        String primaryEmail,
        String displayName,
        String company,
        String tier,
        String industry,
        String country,
        String preferredLanguage,
        String accountManager,
        String contractStatus,
        String contractRenewalDate,
        String paymentStatus,
        long lifetimeValueUsd,
        CommercialHistory history
) {
    /** True when the customer is in the VIP tier. */
    public boolean isVip() {
        return "VIP".equalsIgnoreCase(tier);
    }
}
