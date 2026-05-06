package com.example.salesai.ports;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.EmotionalTone;
import com.example.salesai.domain.RiskAssessment;

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
