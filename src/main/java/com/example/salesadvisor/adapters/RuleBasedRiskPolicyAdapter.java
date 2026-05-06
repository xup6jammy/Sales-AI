package com.example.salesadvisor.adapters;

import com.example.salesadvisor.domain.BusinessIntent;
import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.EmotionalTone;
import com.example.salesadvisor.domain.RiskAssessment;
import com.example.salesadvisor.ports.RiskPolicyPort;
import com.example.salesadvisor.risk.RiskRules;

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
