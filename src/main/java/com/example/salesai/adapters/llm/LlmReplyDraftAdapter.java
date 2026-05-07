package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;
import com.example.salesai.audit.LlmCallAuditEntry;
import com.example.salesai.classify.RuleBasedIntentClassifier;
import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;
import com.example.salesai.ports.AuditLogPort;
import com.example.salesai.ports.ReplyDraftPort;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ReplyDraftPort} implementation that delegates to an {@link LlmClient}
 * to produce two reply drafts labelled exactly {@code "Safe / Formal"} and
 * {@code "Warm / Relationship-Focused"} (canonical labels the renderer
 * depends on), regardless of what label the LLM itself returns.
 *
 * <p>Every call writes a structured {@link LlmCallAuditEntry} to the
 * {@link AuditLogPort} for SOC2 / ISO 27001 traceability.
 *
 * <p>This adapter does NOT perform a risk gate — that responsibility belongs
 * to the workflow layer (Task 4.8). It assumes it is only called when risk
 * is non-HIGH.
 */
public final class LlmReplyDraftAdapter implements ReplyDraftPort {

    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.3;
    private static final List<String> CANONICAL_LABELS =
        List.of("Safe / Formal", "Warm / Relationship-Focused");

    private final LlmClient llm;
    private final AuditLogPort audit;
    private final String apiKeyForFingerprint;
    private final String workflowStep;
    private final RuleBasedIntentClassifier classifier = new RuleBasedIntentClassifier();

    /**
     * @param llm                  the underlying LLM client
     * @param audit                audit log to receive {@link LlmCallAuditEntry} after each call
     * @param apiKeyForFingerprint API key to fingerprint in audit (may be {@code null} for local LLMs)
     * @param workflowStep         human-readable step name written into the audit entry
     */
    public LlmReplyDraftAdapter(LlmClient llm, AuditLogPort audit,
                                 String apiKeyForFingerprint, String workflowStep) {
        this.llm = llm;
        this.audit = audit;
        this.apiKeyForFingerprint = apiKeyForFingerprint;
        this.workflowStep = workflowStep;
    }

    @Override
    public List<ReplyDraft> generate(
            CustomerProfile customer, EmailThread thread, ReplyStrategy strategy) {

        // Step 1: classify intent via rule-based classifier
        BusinessIntent intent = classifier.classifyIntent(thread);

        // Step 2: build placeholder risk hint — real risk gate is in workflow (Task 4.8)
        RiskAssessment riskHint = new RiskAssessment(
            RiskLevel.LOW,
            List.of("(workflow has already gated; LLM only sees non-HIGH risk)"),
            false, List.of());

        // Step 3: build prompts and request
        String userPrompt = PromptBuilder.userPrompt(customer, thread, intent, riskHint);
        LlmRequest req = new LlmRequest(
            PromptBuilder.systemPrompt(), userPrompt,
            MAX_TOKENS, TEMPERATURE, llm.defaultModel());

        // Step 4: call LLM, wrapping IOException as RuntimeException with audit log
        String requestId = "req_" + UUID.randomUUID().toString().substring(0, 8);
        LlmResponse resp;
        try {
            resp = llm.complete(req);
        } catch (IOException e) {
            audit.log("LLM_ERROR", llm.providerName() + ": " + e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }

        // Step 5: write audit entry
        writeAudit(req, resp, requestId);

        // Step 6: parse and canonicalize labels
        return parseDrafts(resp.text());
    }

    private void writeAudit(LlmRequest req, LlmResponse resp, String requestId) {
        String fullPrompt = req.systemPrompt() + "\n" + req.userPrompt();
        String hash = sha256(fullPrompt);
        double cost = estimateCostUsd(llm.providerName(), resp.model(),
            resp.inputTokens(), resp.outputTokens());
        audit.log(new LlmCallAuditEntry(
            Instant.now(),
            llm.providerName(),
            resp.model(),
            hash,
            fullPrompt.length(),
            resp.text().length(),
            resp.inputTokens(),
            resp.outputTokens(),
            resp.latencyMs(),
            cost,
            apiKeyForFingerprint == null ? "(none)"
                : LlmCallAuditEntry.fingerprint(apiKeyForFingerprint),
            workflowStep,
            requestId));
    }

    /**
     * Parses the LLM's JSON drafts response. Assigns canonical labels
     * ({@code "Safe / Formal"} and {@code "Warm / Relationship-Focused"}) to
     * the first two drafts regardless of what the LLM put in the label/strategy
     * field, so the downstream renderer's label-based logic stays stable.
     */
    private static List<ReplyDraft> parseDrafts(String text) {
        try {
            Map<String, Object> root = MiniJson.asObject(MiniJson.parse(text.strip()));
            List<?> raw = (List<?>) root.getOrDefault("drafts", List.of());
            List<ReplyDraft> out = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                Map<String, Object> m = MiniJson.asObject(raw.get(i));
                String label = i < CANONICAL_LABELS.size()
                    ? CANONICAL_LABELS.get(i)
                    : MiniJson.asString(m.getOrDefault("label",
                        m.getOrDefault("strategy", "draft-" + i)));
                String subject = MiniJson.asString(m.getOrDefault("subject", ""));
                String body = MiniJson.asString(m.getOrDefault("body", ""));
                out.add(new ReplyDraft(label, subject, body));
            }
            return out;
        } catch (RuntimeException e) {
            throw new RuntimeException("LLM returned unparseable JSON: " + e.getMessage(), e);
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "(no-sha256)";
        }
    }

    /** Rough cost estimates as of 2026-Q2. Update when provider pricing changes. */
    private static double estimateCostUsd(String provider, String model, int inT, int outT) {
        return switch (provider) {
            case "anthropic" -> (inT * 3e-6) + (outT * 15e-6);   // Claude Sonnet
            case "openai"    -> (inT * 2.5e-6) + (outT * 10e-6); // gpt-4o
            case "gemini"    -> (inT * 1.25e-6) + (outT * 5e-6); // 1.5 Pro
            default          -> 0.0;                              // local LLM = free
        };
    }
}
