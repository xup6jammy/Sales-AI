package com.example.salesai.adapters;

import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.ports.ReplyDraftPort;
import com.example.salesai.reply.ReplyComposer;

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
