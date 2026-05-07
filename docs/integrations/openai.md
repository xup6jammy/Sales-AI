# OpenAI (GPT) Integration

## What This Enables

Passing `--llm openai` causes Sales-AI to call the OpenAI Chat Completions API
after the risk gate approves a reply, producing a real LLM-drafted email body
instead of the built-in template filler. The draft is embedded in the audit
entry, written to the reply file, and (if wired) dispatched through your email
adapter.

---

## Prerequisites

- Java 21 runtime (`java -version`)
- An OpenAI API key (see below)
- Network access to `api.openai.com` on port 443

---

## Get an API Key

1. Go to **platform.openai.com** and sign in (or create an account).
2. Navigate to **API Keys** in the left sidebar (under your organization).
3. Click **Create new secret key**, optionally scope it to specific projects.
4. Copy the key immediately — it is shown only once.
5. Store it in your secrets manager or export it as an environment variable.

---

## Recommended Model

Sales-AI defaults to **`gpt-4o-2024-08-06`**.

Reasons for this choice:

- **Structured output support** — This model snapshot supports OpenAI's
  `response_format: json_schema` mode, ensuring strict JSON adherence without
  extra prompt engineering.
- **Speed** — `gpt-4o` is OpenAI's fastest frontier model; median latency for
  a 1000-token context is typically under 2 seconds.
- **Cost efficiency** — Cheaper than GPT-4-Turbo while matching or exceeding
  its quality on B2B email tasks.

You can override the model with `--llm-model <model-id>`.

---

## Wiring It Up

```sh
export OPENAI_API_KEY="sk-..."

java -cp out com.example.salesai.SalesAiCli \
  --llm openai \
  --email vip@enterprise.com
```

With an explicit model override:

```sh
java -cp out com.example.salesai.SalesAiCli \
  --llm openai \
  --llm-model gpt-4o-mini \
  --email vip@enterprise.com
```

The CLI reads `OPENAI_API_KEY` from the environment. No config file needed.

---

## Cost Expectations

OpenAI bills per million tokens processed.

| Token type    | Rate (approx.)         |
|---------------|------------------------|
| Input tokens  | $2.50 / 1M tokens      |
| Output tokens | $10.00 / 1M tokens     |

A typical Sales-AI call (system prompt + email thread context + 300-token reply)
uses roughly **800 input tokens** and **350 output tokens**:

> ~$0.002 input + ~$0.0035 output ≈ **~$0.006 per call**

With OpenAI's **Prompt Caching** (automatic for repeated prefixes ≥1024 tokens),
cached input tokens drop by 50%, pushing per-call cost toward **~$0.003**.

---

## Audit Log Entry

Every LLM call writes a structured entry to the audit log. For OpenAI calls,
the entry includes:

| Field              | Example value                              |
|--------------------|--------------------------------------------|
| `provider`         | `openai`                                   |
| `model`            | `gpt-4o-2024-08-06`                        |
| `inputTokens`      | `798`                                      |
| `outputTokens`     | `342`                                      |
| `latencyMs`        | `1523`                                     |
| `estimatedCostUsd` | `0.0054`                                   |
| `keyFingerprint`   | `...b7c2` (last 4 chars of the API key)    |
| `promptSha256`     | `e3b0c44298fc1c149afb...` (hex, truncated) |
| `requestId`        | `chatcmpl-...` (OpenAI request ID)         |
| `workflowStep`     | `DRAFT_REPLY`                              |

This audit trail satisfies traceability requirements for **SOC 2 Type II** and
**ISO 27001** — you can reconstruct exactly which key, which model, and which
prompt produced any given output.

---

## Privacy and Data Handling

Using `--llm openai` sends message body content to OpenAI's API servers
(US-based by default). OpenAI does not use API traffic to train models unless
you explicitly opt in, but the data physically leaves your network perimeter.

OpenAI offers **data processing addendums (DPA)** and enterprise zero-retention
options, but at the API level data is still transmitted to their infrastructure.

If your deployment is in a **regulated industry** (banking, insurance,
healthcare, ERP) or subject to strict GDPR/data-residency rules, consider
using a local LLM instead. See [`local-llm.md`](./local-llm.md).

---

## Troubleshooting

**401 Unauthorized**
The API key is missing, malformed, or has been revoked. Verify with:
```sh
echo $OPENAI_API_KEY   # should print sk-...
```
Regenerate the key in platform.openai.com → API Keys if needed. Also check
that you have billing configured — new accounts without a payment method will
get 401s even with a valid key.

**429 Too Many Requests**
You have hit your tier's rate limit or spend limit. Options:
- Wait and retry (Sales-AI does not auto-retry yet).
- Increase your usage tier in the OpenAI platform billing settings.
- Switch to `gpt-4o-mini` which has higher default RPM limits.

**JSON parse error in LlmReplyDraftService**
The model returned a response that did not match the expected JSON schema.
This is rare when using `json_schema` response format. If it recurs:
1. Check whether `--llm-model` is set to an older model that does not support
   structured output (e.g. `gpt-3.5-turbo`).
2. File an issue with the full `promptSha256` from the audit log.
