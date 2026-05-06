package com.example.salesadvisor.ports;

/**
 * Port for recording an interaction summary back to the CRM. The MVP
 * has a no-op adapter that just routes the call to the audit log.
 */
public interface CrmPort {
    void recordInteraction(String customerId, String summary);
}
