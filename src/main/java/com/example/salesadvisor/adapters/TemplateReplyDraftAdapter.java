package com.example.salesadvisor.adapters;

import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.ReplyDraft;
import com.example.salesadvisor.domain.ReplyStrategy;
import com.example.salesadvisor.domain.RiskAssessment;
import com.example.salesadvisor.ports.ReplyDraftPort;
import com.example.salesadvisor.reply.ReplyComposer;

import java.util.List;

/**
 * Reply draft adapter that delegates to {@link ReplyComposer}.
 *
 * <p>Holds an optional {@link RiskAssessment} so it can refuse to
 * include refund / contract concessions when the risk gate has
 * blocked them. The workflow is responsible for setting it before
 * calling {@link #generate}.
 */
public final class TemplateReplyDraftAdapter implements ReplyDraftPort {

    private final ReplyComposer composer = new ReplyComposer();
    private RiskAssessment riskHint;

    /** Used by the workflow to share the latest risk assessment. */
    public void setRiskAssessment(RiskAssessment risk) {
        this.riskHint = risk;
    }

    @Override
    public List<ReplyDraft> generate(
            CustomerProfile customer,
            EmailThread thread,
            ReplyStrategy strategy) {
        return composer.compose(customer, thread, strategy, riskHint);
    }
}
