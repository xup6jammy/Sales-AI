package com.example.salesadvisor.domain;

/**
 * Input to {@code AdvisorWorkflow.run}. {@code approvalProvided} is true
 * when a human (typically a manager) has explicitly approved the reply.
 */
public record AdvisorRequest(
        String customerEmail,
        boolean approvalProvided
) {}
