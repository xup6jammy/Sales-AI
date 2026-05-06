package com.example.salesadvisor.ports;

import com.example.salesadvisor.domain.BusinessIntent;
import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.EmotionalTone;
import com.example.salesadvisor.domain.RiskAssessment;

/**
 * Port that decides the risk of replying to this email autonomously.
 */
public interface RiskPolicyPort {
    RiskAssessment evaluate(
            CustomerProfile customer,
            EmailThread thread,
            BusinessIntent intent,
            EmotionalTone tone
    );
}
