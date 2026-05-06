package com.example.salesai.domain;

/**
 * A single drafted reply. The {@code label} disambiguates between the
 * "Safe / Formal" and "Warm / Relationship-Focused" options.
 */
public record ReplyDraft(
        String label,
        String subject,
        String body
) {}
