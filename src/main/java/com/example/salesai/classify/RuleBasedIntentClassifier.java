package com.example.salesai.classify;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.EmotionalTone;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bilingual (English + 繁體中文) keyword-based classifier.
 *
 * <p>The most recent inbound message gets the highest weight; earlier
 * messages contribute lightly. When two intents tie on score, the
 * higher-stakes intent wins per the priority list defined below.
 */
public final class RuleBasedIntentClassifier {

    /** Higher index == higher stakes; used as a tie-breaker. */
    private static final List<BusinessIntent> INTENT_PRIORITY = List.of(
            BusinessIntent.UNKNOWN,
            BusinessIntent.INQUIRY,
            BusinessIntent.QUOTATION,
            BusinessIntent.NEGOTIATION,
            BusinessIntent.TECHNICAL_SUPPORT,
            BusinessIntent.DELIVERY_DELAY,
            BusinessIntent.PAYMENT_ISSUE,
            BusinessIntent.RENEWAL,
            BusinessIntent.COMPLAINT,
            BusinessIntent.CHURN_RISK
    );

    private static final Map<BusinessIntent, List<String>> INTENT_KEYWORDS =
            new EnumMap<>(BusinessIntent.class);

    private static final Map<EmotionalTone, List<String>> TONE_KEYWORDS =
            new EnumMap<>(EmotionalTone.class);

    static {
        INTENT_KEYWORDS.put(BusinessIntent.COMPLAINT, List.of(
                "unacceptable", "frustrated", "angry", "complain",
                "抱怨", "不滿", "escalat"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.CHURN_RISK, List.of(
                "alternative vendors", "alternative vendor", "cancel",
                "解約", "exit clause", "switch provider",
                "pause the renewal", "pause renewal",
                "evaluating alternative", "終止合作"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.RENEWAL, List.of(
                "renewal", "renew", "續約", "next term", "extend the contract"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.PAYMENT_ISSUE, List.of(
                "invoice", "overdue", "逾期", "payment", "credit terms",
                "past due"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.DELIVERY_DELAY, List.of(
                "delay", "delayed", "late", "ETA", "延遲", "logistic",
                "missed eta", "pushed back", "shipment"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.TECHNICAL_SUPPORT, List.of(
                "firmware", "ticket", "SUP-", "misalignment", "bug",
                "技術", "故障", "vision module", "support ticket"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.NEGOTIATION, List.of(
                "discount", "折扣", "price", "pricing", "rebate", "concession"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.INQUIRY, List.of(
                "interested in", "詢問", "inquiry", "enquiry"
        ));
        INTENT_KEYWORDS.put(BusinessIntent.QUOTATION, List.of(
                "quote", "quotation", "offer", "報價", "價格"
        ));

        TONE_KEYWORDS.put(EmotionalTone.ANGRY, List.of(
                "unacceptable", "ridiculous", "outrageous",
                "憤怒", "氣"
        ));
        TONE_KEYWORDS.put(EmotionalTone.FRUSTRATED, List.of(
                "frustrated", "disappointed", "again",
                "second time", "third time", "失望", "frankly"
        ));
        TONE_KEYWORDS.put(EmotionalTone.URGENT, List.of(
                "urgent", "asap", "by tomorrow", "end of week",
                "立刻", "盡快", "by end of week"
        ));
        TONE_KEYWORDS.put(EmotionalTone.APPRECIATIVE, List.of(
                "thank you", "thanks", "appreciate", "感謝"
        ));
        TONE_KEYWORDS.put(EmotionalTone.CONFUSED, List.of(
                "unclear", "confused", "not sure", "不清楚"
        ));
    }

    public BusinessIntent classifyIntent(EmailThread thread) {
        if (thread == null || thread.messages() == null
                || thread.messages().isEmpty()) {
            return BusinessIntent.UNKNOWN;
        }
        EnumMap<BusinessIntent, Integer> scores =
                new EnumMap<>(BusinessIntent.class);
        for (BusinessIntent i : BusinessIntent.values()) {
            scores.put(i, 0);
        }

        // Heaviest weight on the most recent INBOUND message.
        EmailMessage lastInbound = thread.lastInbound().orElse(null);
        if (lastInbound != null) {
            scoreInto(scores, lastInbound.body(), 4);
        }
        // Light weight on every other message (including earlier inbound).
        for (EmailMessage m : thread.messages()) {
            if (m == lastInbound) {
                continue;
            }
            scoreInto(scores, m.body(), 1);
        }
        // Subject line gets a small boost.
        scoreInto(scores, thread.subject(), 2);

        BusinessIntent best = BusinessIntent.UNKNOWN;
        int bestScore = 0;
        for (Map.Entry<BusinessIntent, Integer> e : scores.entrySet()) {
            int s = e.getValue();
            if (s == 0) {
                continue;
            }
            if (s > bestScore) {
                best = e.getKey();
                bestScore = s;
                continue;
            }
            if (s == bestScore && priorityRank(e.getKey()) > priorityRank(best)) {
                best = e.getKey();
            }
        }
        return best;
    }

    public EmotionalTone classifyTone(EmailThread thread) {
        if (thread == null || thread.messages() == null
                || thread.messages().isEmpty()) {
            return EmotionalTone.NEUTRAL;
        }
        EnumMap<EmotionalTone, Integer> scores =
                new EnumMap<>(EmotionalTone.class);
        for (EmotionalTone t : EmotionalTone.values()) {
            scores.put(t, 0);
        }
        EmailMessage lastInbound = thread.lastInbound().orElse(null);
        if (lastInbound != null) {
            scoreToneInto(scores, lastInbound.body(), 4);
        }
        for (EmailMessage m : thread.messages()) {
            if (m == lastInbound) {
                continue;
            }
            scoreToneInto(scores, m.body(), 1);
        }

        EmotionalTone best = EmotionalTone.NEUTRAL;
        int bestScore = 0;
        // Tone tie-breaker: severe first.
        List<EmotionalTone> tonePriority = List.of(
                EmotionalTone.NEUTRAL,
                EmotionalTone.APPRECIATIVE,
                EmotionalTone.CONFUSED,
                EmotionalTone.URGENT,
                EmotionalTone.FRUSTRATED,
                EmotionalTone.ANGRY
        );
        for (Map.Entry<EmotionalTone, Integer> e : scores.entrySet()) {
            int s = e.getValue();
            if (s == 0) {
                continue;
            }
            if (s > bestScore) {
                best = e.getKey();
                bestScore = s;
                continue;
            }
            if (s == bestScore
                    && tonePriority.indexOf(e.getKey())
                            > tonePriority.indexOf(best)) {
                best = e.getKey();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private static int priorityRank(BusinessIntent intent) {
        int idx = INTENT_PRIORITY.indexOf(intent);
        return idx < 0 ? 0 : idx;
    }

    private static void scoreInto(
            Map<BusinessIntent, Integer> scores,
            String body,
            int weight) {
        if (body == null || body.isEmpty()) {
            return;
        }
        String lc = body.toLowerCase(Locale.ROOT);
        for (Map.Entry<BusinessIntent, List<String>> e
                : INTENT_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (lc.contains(kw.toLowerCase(Locale.ROOT))) {
                    scores.merge(e.getKey(), weight, Integer::sum);
                }
            }
        }
    }

    private static void scoreToneInto(
            Map<EmotionalTone, Integer> scores,
            String body,
            int weight) {
        if (body == null || body.isEmpty()) {
            return;
        }
        String lc = body.toLowerCase(Locale.ROOT);
        for (Map.Entry<EmotionalTone, List<String>> e
                : TONE_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (lc.contains(kw.toLowerCase(Locale.ROOT))) {
                    scores.merge(e.getKey(), weight, Integer::sum);
                }
            }
        }
    }
}
