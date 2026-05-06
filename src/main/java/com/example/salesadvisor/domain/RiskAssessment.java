package com.example.salesadvisor.domain;

import java.util.List;

/**
 * Output of the risk policy. Lists the human-readable triggers that
 * led to the level, and the actions that must NOT appear in a draft.
 */
public record RiskAssessment(
        RiskLevel level,
        List<String> reasons,
        boolean requiresManagerApproval,
        List<String> blockedActions
) {}
