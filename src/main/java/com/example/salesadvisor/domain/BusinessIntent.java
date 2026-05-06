package com.example.salesadvisor.domain;

/**
 * Coarse-grained classification of what the customer is trying to do
 * in an email thread. Used to drive the reply strategy.
 */
public enum BusinessIntent {
    INQUIRY,
    QUOTATION,
    COMPLAINT,
    RENEWAL,
    PAYMENT_ISSUE,
    DELIVERY_DELAY,
    TECHNICAL_SUPPORT,
    NEGOTIATION,
    CHURN_RISK,
    UNKNOWN
}
