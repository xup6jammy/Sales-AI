package com.example.salesai.ports;

import com.example.salesai.domain.AdvisorRequest;
import com.example.salesai.domain.RiskAssessment;

/**
 * Port that decides whether the drafted reply is allowed to leave
 * the building.
 *
 * <p>Contract: returns {@code true} when:
 * <ul>
 *   <li>the risk assessment does NOT require manager approval, OR</li>
 *   <li>{@code request.approvalProvided()} is true.</li>
 * </ul>
 *
 * <p>It is a deliberate non-goal of this port to "silently bypass"
 * refund/legal/contract gates. Those triggers always escalate to
 * {@link com.example.salesai.domain.RiskLevel#REQUIRES_MANAGER_APPROVAL}
 * inside the policy and, if the human approver still says yes, the
 * intent is logged in the audit trail.
 */
public interface ApprovalPort {
    boolean isApproved(RiskAssessment risk, AdvisorRequest request);
}
