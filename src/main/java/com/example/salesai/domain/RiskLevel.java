package com.example.salesai.domain;

/**
 * Risk level emitted by the risk policy. The level decides whether
 * an automated draft is allowed to leave the building.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    REQUIRES_MANAGER_APPROVAL;

    /**
     * @return true when the risk level is high enough that an auto-draft
     * must not be sent without explicit human approval.
     */
    public boolean blocksAutoDraft() {
        return this == HIGH || this == REQUIRES_MANAGER_APPROVAL;
    }
}
