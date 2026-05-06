package com.example.salesadvisor.ports;

import com.example.salesadvisor.domain.EmailThread;

import java.util.Optional;

/**
 * Port for loading the most recent email thread that includes the
 * given customer.
 */
public interface EmailThreadPort {
    Optional<EmailThread> loadLatestForCustomer(String customerEmail);
}
