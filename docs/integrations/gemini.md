# Google Gemini Integration

## What This Enables

Passing `--llm gemini` causes Sales-AI to call the Google Generative AI API
after the risk gate approves a reply, producing a real LLM-drafted email body
instead of the built-in template filler. The draft is embedded in the audit
entry, written to the reply file, and (if wired) dispatched through your email
adapter.

---

## Prerequisites

- Java 21 runtime (`java -version`)
- A Google AI Studio API key **or** a Vertex AI service account (see below)
- Network access to `generativelanguage.googleapis.com` on port 443
  (AI Studio) or `<region>-aiplatform.googleapis.com` (Vertex AI)

---

## Get an API Key

### Option A — Google AI Studio (simplest, not for production at scale)

1. Go to **aistudio.google.com** and sign in with a Google account.
2. Click **Get API Key** in the top-right corner.
3. Choose **Create API key in new project** or select an existing GCP project.
4. Copy the key and store it in your secrets manager.

### Option B — Vertex AI (recommended for enterprise/GCP-native deployments)

1. Enable the **Vertex AI API** in your GCP project.
2. Create a service account with the `Vertex AI User` role.
3. Download the JSON key file and set `GOOGLE_APPLICATION_CREDENTIALS` to its path.
4. Set `--llm-endpoint` to your Vertex endpoint if not using the default.

For most operators starting out, AI Studio is the fastest path. Move to Vertex
when you need VPC-SC controls, audit logs in Cloud Logging, or regional
data residency within GCP.

---

## Recommended Model

Sales-AI defaults to **`gemini-1.5-pro-002`**.

Reasons for this choice:

- **Long context window** — 1M token context is useful when the email thread
  is long or you pass extensive CRM context as background.
- **JSON mode support** — The model supports `responseMimeType: application/json`
  for strict schema adherence, comparable to OpenAI's structured outputs.
- **Quality** — Gemini 1.5 Pro matches GPT-4o on most B2B writing benchmarks
  while offering competitive pricing at high volume.

You can override the model with `--llm-model <model-id>` (e.g. `gemini-2.0-flash`
for lower latency and cost at some quality tradeoff).

---

## Wiring It Up

```sh
export GEMINI_API_KEY="AIza..."

java -cp out com.example.salesai.SalesAiCli \
  --llm gemini \
  --email vip@enterprise.com
```

With an explicit model override:

```sh
java -cp out com.example.salesai.SalesAiCli \
  --llm gemini \
  --llm-model gemini-2.0-flash \
  --email vip@enterprise.com
```

The CLI reads `GEMINI_API_KEY` from the environment. No config file needed for
the AI Studio path.

---

## Cost Expectations

Google bills per million tokens processed (rates for AI Studio / standard tier).

| Token type    | Rate (approx.)         |
|---------------|------------------------|
| Input tokens  | $1.25 / 1M tokens      |
| Output tokens | $5.00 / 1M tokens      |

A typical Sales-AI call (system prompt + email thread context + 300-token reply)
uses roughly **800 input tokens** and **350 output tokens**:

> ~$0.001 input + ~$0.00175 output ≈ **~$0.003 per call**

Gemini is the most cost-efficient of the three cloud providers at standard
scale. Google also offers a free tier via AI Studio suitable for development
and low-volume testing (rate-limited, not for production).

---

## Audit Log Entry

Every LLM call writes a structured entry to the audit log. For Gemini calls,
the entry includes:

| Field              | Example value                              |
|--------------------|--------------------------------------------|
| `provider`         | `gemini`                                   |
| `model`            | `gemini-1.5-pro-002`                       |
| `inputTokens`      | `805`                                      |
| `outputTokens`     | `338`                                      |
| `latencyMs`        | `2104`                                     |
| `estimatedCostUsd` | `0.0027`                                   |
| `keyFingerprint`   | `...z9k1` (last 4 chars of the API key)    |
| `promptSha256`     | `e3b0c44298fc1c149afb...` (hex, truncated) |
| `requestId`        | Gemini-returned response ID                |
| `workflowStep`     | `DRAFT_REPLY`                              |

This audit trail satisfies traceability requirements for **SOC 2 Type II** and
**ISO 27001** — you can reconstruct exactly which key, which model, and which
prompt produced any given output.

---

## Privacy and Data Handling

Using `--llm gemini` sends message body content to Google's API servers.
Google states that API data is not used to train models and offers a **data
processing addendum** for enterprise customers. However, data physically leaves
your network perimeter and transits Google's infrastructure.

For GCP customers who need regional data residency, Vertex AI with VPC-SC
provides stronger controls than the AI Studio path.

If your deployment is in a **regulated industry** (banking, insurance,
healthcare, ERP) or subject to strict GDPR/data-residency rules, consider
using a local LLM instead. See [`local-llm.md`](./local-llm.md).

---

## Troubleshooting

**401 Unauthorized / API_KEY_INVALID**
The API key is missing, malformed, or has been revoked. Verify with:
```sh
echo $GEMINI_API_KEY   # should print AIza...
```
Regenerate the key in aistudio.google.com → API Keys if needed.

**429 Too Many Requests / RESOURCE_EXHAUSTED**
You have hit the free-tier QPM (queries per minute) limit or your paid quota.
Options:
- Wait and retry (Sales-AI does not auto-retry yet).
- Enable billing on your GCP project to unlock higher quotas.
- Switch to `gemini-2.0-flash` which has a higher default QPM limit.

**JSON parse error in LlmReplyDraftService**
The model returned a response that did not match the expected JSON schema.
This is rare when `responseMimeType: application/json` is enabled. If it recurs:
1. Check whether `--llm-model` is set to a model that does not support JSON mode
   (e.g. older `gemini-pro` snapshots).
2. File an issue with the full `promptSha256` from the audit log.
