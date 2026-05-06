package com.example.salesadvisor.adapters;

import com.example.salesadvisor.ports.AuditLogPort;
import com.example.salesadvisor.ports.CrmPort;

/**
 * CRM adapter that doesn't actually call out to a CRM. It records
 * every interaction it would have logged into the audit trail, so
 * the report still shows that the workflow asked the CRM port to
 * note this email.
 */
public final class NoopCrmAdapter implements CrmPort {

    private final AuditLogPort audit;

    public NoopCrmAdapter(AuditLogPort audit) {
        this.audit = audit;
    }

    @Override
    public void recordInteraction(String customerId, String summary) {
        audit.log("CRM_RECORD",
                "customerId=" + customerId + " summary=" + summary);
    }
}
