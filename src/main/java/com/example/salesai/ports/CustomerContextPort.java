package com.example.salesai.ports;

import com.example.salesai.domain.CustomerProfile;

import java.util.Optional;

/**
 * Port for resolving an email address to a customer profile. Real
 * implementations would call into a CRM; the MVP uses a JSON file.
 */
public interface CustomerContextPort {
    Optional<CustomerProfile> findByEmail(String email);
}
