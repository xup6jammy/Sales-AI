package com.example.salesadvisor.adapters;

import com.example.salesadvisor.domain.AdvisorRequest;
import com.example.salesadvisor.domain.RiskAssessment;
import com.example.salesadvisor.ports.ApprovalPort;
import com.example.salesadvisor.ports.AuditLogPort;

/**
 * Approval adapter that requires a human flag for risky actions.
 *
 * <p>Logic:
 * <ul>
 *   <li>If the risk does not require manager approval: always approved.</li>
 *   <li>If it does require approval and the request did not provide it:
 *       denied. The audit log records {@code APPROVAL_DENIED}.</li>
 *   <li>If it does require approval and the request did provide it:
 *       approved. The audit log records {@code APPROVAL_GRANTED} and
 *       includes the reasons so the trail is honest.</li>
 * </ul>
 */
public final class ManualApprovalAdapter implements ApprovalPort {

    private final AuditLogPort audit;

    public ManualApprovalAdapter(AuditLogPort audit) {
        this.audit = audit;
    }

    @Override
    public boolean isApproved(RiskAssessment risk, AdvisorRequest request) {
        if (risk == null || !risk.requiresManagerApproval()) {
            audit.log("APPROVAL_NOT_REQUIRED",
                    "risk level=" + (risk == null ? "n/a" : risk.level()));
            return true;
        }
        if (request != null && request.approvalProvided()) {
            audit.log("APPROVAL_GRANTED",
                    "manager flag=true; reasons=" + risk.reasons());
            return true;
        }
        audit.log("APPROVAL_DENIED",
                "manager flag=false; reasons=" + risk.reasons());
        return false;
    }
}
