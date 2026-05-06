package com.example.salesai.adapters;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.EmotionalTone;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.ports.RiskPolicyPort;
import com.example.salesai.risk.RiskRules;

/**
 * Risk policy adapter that delegates the rule logic to {@link RiskRules}
 * and packages the outcome as a {@link RiskAssessment}.
 */
public final class RuleBasedRiskPolicyAdapter implements RiskPolicyPort {

    @Override
    public RiskAssessment evaluate(
            CustomerProfile customer,
            EmailThread thread,
            BusinessIntent intent,
            EmotionalTone tone) {
        RiskRules.Outcome o = RiskRules.evaluate(customer, thread, intent, tone);
        return new RiskAssessment(
                o.level(),
                o.reasons(),
                o.requiresManagerApproval(),
                o.blockedActions()
        );
    }
}
