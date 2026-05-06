package com.example.salesai.ports;

import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;

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
