package com.example.salesadvisor.domain;

import java.util.List;

/**
 * The advisor's recommendation about how to reply: tone, posture,
 * what NOT to say, what we're allowed to commit to, and the single
 * best next action.
 */
public record ReplyStrategy(
        String tone,
        String position,
        List<String> avoidSaying,
        List<String> allowedCommitments,
        String nextBestAction
) {}
