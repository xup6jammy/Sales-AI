package com.example.salesadvisor.domain;

import java.util.List;

/**
 * Everything the advisor produces in a single run. Designed to be
 * trivially renderable by {@code AdvisorReportRenderer}.
 *
 * <p>Fields may be {@code null} when the workflow short-circuits
 * early (e.g. customer not found).
 */
public record AdvisorResult(
        CustomerProfile customer,
        CommercialHistory history,
        EmailThread thread,
        BusinessIntent intent,
        EmotionalTone tone,
        RiskAssessment risk,
        ReplyStrategy strategy,
        List<ReplyDraft> drafts,
        List<FollowUpAction> followUps,
        boolean draftDeliveryBlocked,
        boolean approvalGranted,
        List<String> auditTrail
) {}
