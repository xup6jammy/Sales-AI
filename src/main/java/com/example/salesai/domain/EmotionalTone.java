package com.example.salesai.domain;

/**
 * Emotional tone detected in the most recent inbound message.
 * Used together with {@link BusinessIntent} to shape reply tone.
 */
public enum EmotionalTone {
    NEUTRAL,
    FRUSTRATED,
    ANGRY,
    URGENT,
    APPRECIATIVE,
    CONFUSED
}
