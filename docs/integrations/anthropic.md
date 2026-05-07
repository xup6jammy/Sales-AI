# Anthropic (Claude) Integration

## What This Enables

Passing `--llm anthropic` causes Sales-AI to call the Anthropic API after the
risk gate approves a reply, producing a real LLM-drafted email body instead of
the built-in template filler. The draft is embedded in the audit entry, written
to the reply file, and (if wired) dispatched through your email adapter.

---

## Prerequisites

- Java 21 runtime (`java -version`)
- An Anthropic API key (see below)
- Network access to `api.anthropic.com` on port 443

---

## Get an API Key

1. Go to **console.anthropic.com** and sign in (or create an account).
2. Navigate to **API Keys** in the left sidebar.
3. Click **Create Key**, give it a descriptive name (e.g. `sales-ai-prod`).
4. Copy the key immediately — it is shown only once.
5. Store it in your secrets manager or export it as an environment variable.

---

## Recommended Model

Sales-AI defaults to **`claude-3-5-sonnet-20241022`**.

Reasons for this choice:

- **Structured JSON adherence** — Sonnet 3.5 reliably returns the strict JSON
  schema Sales-AI expects without needing extra retry logic.
- **Safety instruction following** — The model respects system-level
  constraints (tone, length, no hallucinated pricing) with high consistency.
- **Speed/cost balance** — Sonnet is meaningfully faster and cheaper than Opus
  while retaining near-Opus quality for B2B email drafting.

You can override the model with `--llm-model <model-id>` if you want to test
Opus for highest quality or Haiku for lowest latency.

---

## Wiring It Up

```sh
export ANTHROPIC_API_KEY="sk-ant-..."

java -cp out com.example.salesai.SalesAiCli \
  --llm anthropic \
  --email vip@enterprise.com
```

With an explicit model override:

```sh
java -cp out com.example.salesai.SalesAiCli \
  --llm anthropic \
  --llm-model claude-opus-4-5 \
  --email vip@enterprise.com
```

The CLI reads `ANTHROPIC_API_KEY` from the environment. No config file needed.

---

## Cost Expectations

Anthropic bills per million tokens processed.

| Token type    | Rate (approx.)         |
|---------------|------------------------|
| Input tokens  | $3.00 / 1M tokens      |
| Output tokens | $15.00 / 1M tokens     |

A typical Sales-AI call (system prompt + email thread context + 300-token reply)
uses roughly **800 input tokens** and **350 output tokens**:

> ~$0.0024 input + ~$0.0053 output ≈ **~$0.008 per call** (worst case)

With prompt caching enabled (planned for a future release), repeated system
prompt tokens drop to ~10% of the base rate, pushing per-call cost to roughly
**$0.003-0.004**.

---

## Audit Log Entry

Every LLM call writes a structured entry to the audit log. For Anthropic calls,
the entry includes:

| Field              | Example value                              |
|--------------------|--------------------------------------------|
| `provider`         | `anthropic`                                |
| `model`            | `claude-3-5-sonnet-20241022`               |
| `inputTokens`      | `812`                                      |
| `outputTokens`     | `347`                                      |
| `latencyMs`        | `1842`                                     |
| `estimatedCostUsd` | `0.0081`                                   |
| `keyFingerprint`   | `...a3f9` (last 4 chars of the API key)    |
| `promptSha256`     | `e3b0c44298fc1c149afb...` (hex, truncated) |
| `requestId`        | API-returned request ID                    |
| `workflowStep`     | `DRAFT_REPLY`                              |

This audit trail satisfies traceability requirements for **SOC 2 Type II** and
**ISO 27001** — you can reconstruct exactly which key, which model, and which
prompt produced any given output.

---

## Privacy and Data Handling

Using `--llm anthropic` sends message body content to Anthropic's API servers
(US-based by default). Anthropic does not use API traffic to train models and
offers a **zero data retention (ZDR)** option for enterprise agreements, but
the data physically leaves your network perimeter.

If your deployment is in a **regulated industry** (banking, insurance,
healthcare, ERP) or subject to strict GDPR/data-residency rules, consider
using a local LLM instead. See [`local-llm.md`](./local-llm.md).

---

## Troubleshooting

**401 Unauthorized**
The API key is missing, malformed, or has been revoked. Verify with:
```sh
echo $ANTHROPIC_API_KEY   # should print sk-ant-...
```
Regenerate the key in console.anthropic.com → API Keys if needed.

**429 Too Many Requests**
You have hit your tier's rate limit. Options:
- Wait and retry (Sales-AI does not auto-retry yet).
- Request a rate-limit increase in the Anthropic console.
- Switch to a lower-tier model (e.g. Haiku) which has higher default limits.

**JSON parse error in LlmReplyDraftService**
The model returned a response that did not match the expected JSON schema.
This is rare with Sonnet 3.5. If it recurs:
1. Check whether you have overridden `--llm-model` to a smaller/older model.
2. File an issue with the full `promptSha256` from the audit log so the prompt
   can be reproduced and tightened.
