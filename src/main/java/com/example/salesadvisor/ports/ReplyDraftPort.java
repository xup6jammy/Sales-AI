package com.example.salesadvisor.ports;

import com.example.salesadvisor.domain.CustomerProfile;
import com.example.salesadvisor.domain.EmailThread;
import com.example.salesadvisor.domain.ReplyDraft;
import com.example.salesadvisor.domain.ReplyStrategy;

import java.util.List;

/**
 * Port that produces draft replies. Implementations MUST return at
 * least two drafts labelled {@code "Safe / Formal"} and
 * {@code "Warm / Relationship-Focused"} so the renderer can rely on
 * both options being present.
 */
public interface ReplyDraftPort {
    List<ReplyDraft> generate(
            CustomerProfile customer,
            EmailThread thread,
            ReplyStrategy strategy
    );
}
