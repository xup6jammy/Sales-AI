# Phase 2 + Phase 4 — Real Email & LLM Integrations

**Status:** Design — pending implementation
**Date:** 2026-05-07
**Scope:** This spec covers Phase 2 (real email via MCP) and Phase 4 (real LLM via HTTP API) as a single combined work item. They share configuration, deployment story, and the new "engine becomes a real product" narrative — so they ship together.

---

## 1. Why now

Today the engine has eight ports. Only one (`CustomerContextPort`) is wired to a real backend (JDBC). The other two integration-critical ports are still mock:

- `EmailThreadPort` — only `MockEmailThreadAdapter` exists, reading a local JSON file
- `ReplyDraftPort` — only `TemplateReplyDraftAdapter` exists, producing replies from hard-coded string templates with zero LLM involvement

The repo's positioning ("an AI sales agent that reads customer email like a senior account manager") promises both real email ingestion and real LLM-drafted replies. Without this work, the project is a well-architected demo, not a usable product.

This spec also commits the project to a specific manifesto, restated in Section 2.

---

## 2. Design constraints (the manifesto)

Every architectural decision in this spec must conform to the following constraints. They are non-negotiable because they are the project's value proposition.

1. **AI 處理量,人處理 nuance.** The product augments account managers, not replaces them. Every reply path ends with manual approval.
2. **Pre-load context before drafting.** Customer tier, contract state, payment history, ticket history, recent orders are loaded *before* the LLM is asked to write anything.
3. **Two drafts, never one.** The user picks; the system does not decide.
4. **Manager-approval gate is a hard architectural constraint.** Refunds, contract changes, legal language, VIP churn signals must block drafts. The LLM cannot bypass this — the gate runs *before* the LLM is invoked.
5. **Audit trail is end-to-end.** Every AI decision (rule-based or LLM-based) is traceable to its input. SOC2 / ISO 27001 / banking regulator queries can be answered.
6. **Java 21, zero Java dependencies.** The engine itself uses only the JDK. (`java.net.http` is built in; no SDK needed for the LLM HTTP calls.) Deployment may require Node.js for MCP servers — this is documented, not hidden.
7. **Cloud LLM is opt-in.** Regulated industries must have a path that does not send customer data to a third-party cloud. This is supported via local LLM through OpenAI-compatible endpoint.

---

## 3. Architecture

### 3.1 The new shape

```
                          ┌─────────────────────────────────────────┐
                          │         Sales-AI Engine (Java 21)        │
                          │                                          │
  ┌───────────┐  spawn   │  ┌────────────────┐                      │
  │ Gmail MCP │ ◀────────┼──│  McpClient      │                     │
  │  server   │  JSON-RPC│  └────────────────┘                      │
  └───────────┘  /stdio  │           │                              │
                          │           ▼                              │
  ┌───────────┐  spawn   │  ┌────────────────┐    ┌──────────────┐ │
  │Outlook MCP│ ◀────────┼──│  McpClient      │◀──▶│  Workflow    │ │
  │  server   │           │  └────────────────┘    │  + risk gate │ │
  └───────────┘           │                         │  (existing)  │ │
                          │                         └──────┬───────┘ │
                          │                                │         │
                          │                                ▼         │
                          │  ┌──────────────────────────────────┐    │
                          │  │   LlmClient interface             │    │
                          │  │  ┌────────────┬───────────────┐  │    │
                          │  │  │ Anthropic  │  OpenAI       │  │    │
                          │  │  │            │ (also serves  │  │    │
                          │  │  │            │  local LLM)   │  │    │
                          │  │  ├────────────┼───────────────┤  │    │
                          │  │  │ Gemini     │  Template     │  │    │
                          │  │  │            │  (fallback)   │  │    │
                          │  │  └────────────┴───────────────┘  │    │
                          │  └──────────────┬───────────────────┘    │
                          └─────────────────┼─────────────────────────┘
                                            │ HTTPS POST (java.net.http)
                                            ▼
                                ┌────────────────────────┐
                                │  api.anthropic.com /   │
                                │  api.openai.com /      │
                                │  generativelanguage    │
                                │  .googleapis.com /     │
                                │  localhost:11434 (Ollama)│
                                └────────────────────────┘
```

### 3.2 Architectural invariants

These invariants are enforced by code structure, not by convention:

1. `SalesAiCli` knows *no* LLM provider name beyond a CLI flag. It depends only on `LlmClient`.
2. `SalesAiCli` knows *no* email provider name beyond a CLI flag. It depends only on `EmailThreadPort`.
3. `AdvisorWorkflow` calls `LlmClient` *only when* `RiskAssessment.level() != HIGH`. This is a hard branch in code, not a runtime check inside the LLM client. The LLM has no path to high-risk requests.
4. Every `LlmClient.complete(...)` call writes one `LlmCallAuditEntry` to `AuditLogPort` *after the call returns* (success or failure).
5. MCP server child processes are tracked in a JVM shutdown hook so the engine never leaves zombies.
6. Existing mock adapters and rule-based components are not removed. They remain as fallback / demo / known-good baseline.

### 3.3 Deployment modes

Four modes, each one CLI-flag away:

| Mode | Command (abbreviated) | Use case |
|---|---|---|
| Mock | `java SalesAiCli` | 60-second demo, zero deps |
| DB only | `java SalesAiCli --customer-source jdbc --db ...` | Real customer data, mock email/LLM |
| DB + LLM | `java SalesAiCli --customer-source jdbc --db ... --llm anthropic` | Real customer data + real LLM drafts, email still mock |
| Full | `java SalesAiCli --customer-source jdbc --db ... --llm anthropic --email gmail --mcp-config ./mcp-config.json` | Production: real DB + real Gmail/Outlook + real LLM |

---

## 4. Components

### 4.1 New package — `com.example.salesai.mcp.client`

Engine acts as an MCP client to spawn and call external MCP servers (Gmail, Outlook, future).

| File | Responsibility |
|---|---|
| `McpClient.java` | Spawn child process (via `ProcessBuilder`), perform JSON-RPC `initialize` handshake, expose `listTools()` and `callTool(name, args)`, manage graceful shutdown |
| `McpServerConfig.java` | `record(String name, String command, List<String> args, Map<String,String> env)` — one entry from `mcp-config.json` |
| `McpConfigLoader.java` | Parses `mcp-config.json` (Claude Code-compatible schema) into `Map<String, McpServerConfig>` |
| `JsonRpc.java` | JSON-RPC 2.0 helpers — duplicated from `mcp-server/protocol/` for engine self-containment |
| `StdioBridge.java` | Newline-delimited JSON over child process stdin/stdout, with read timeout |

### 4.2 New package — `com.example.salesai.adapters.email`

| File | Responsibility |
|---|---|
| `McpEmailThreadAdapter.java` | implements `EmailThreadPort`. Calls `McpClient.callTool()` to fetch emails. Same adapter serves Gmail and Outlook — provider differences are absorbed by `EmailMcpToolMapping` |
| `EmailMcpToolMapping.java` | Small enum-based dispatch (not a transformative parser). Maps `Provider.GMAIL → tool name "search"`, `Provider.OUTLOOK → tool name "list_messages"`, etc., and surfaces per-provider response key differences (e.g. `messages` vs `value`) |

### 4.3 New package — `com.example.salesai.adapters.llm`

| File | Responsibility |
|---|---|
| `LlmClient.java` | interface: `LlmResponse complete(LlmRequest req)` |
| `LlmRequest.java` | `record(String systemPrompt, String userPrompt, int maxTokens, double temperature, String model)` |
| `LlmResponse.java` | `record(String text, int inputTokens, int outputTokens, String model, long latencyMs)` |
| `AnthropicLlmClient.java` | POST `https://api.anthropic.com/v1/messages` (header `x-api-key`, header `anthropic-version: 2023-06-01`) |
| `OpenAiLlmClient.java` | POST `https://api.openai.com/v1/chat/completions`. Constructor accepts optional `endpointOverride` URL — same class serves cloud OpenAI *and* local LLM (Ollama, vLLM, llama.cpp — all expose OpenAI-compatible API) |
| `GeminiLlmClient.java` | POST `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}` |
| `LlmReplyDraftAdapter.java` | implements `ReplyDraftPort`. Builds prompt via `PromptBuilder`, calls `LlmClient`, parses JSON response into `List<ReplyDraft>`, writes `LlmCallAuditEntry` |
| `PromptBuilder.java` | Builds short system prompt (text block, hard-coded) + dynamic user prompt from `CustomerProfile`, `EmailThread`, `BusinessIntent`, `RiskAssessment` |
| `LlmCallAuditEntry.java` | `record` with: `timestamp, provider, model, prompt_hash, prompt_length_chars, response_length_chars, input_tokens, output_tokens, latency_ms, estimated_cost_usd, api_key_fingerprint, workflow_step, request_id` |

### 4.4 Existing files modified

| File | Change |
|---|---|
| `SalesAiCli.java` | Add CLI flags: `--email`, `--mcp-config`, `--llm`, `--llm-model`, `--llm-endpoint`. Factory wiring in `main()` instantiates the right adapters |
| `AdvisorWorkflow.java` | **Add risk gate**: `if (risk.level() == RiskLevel.HIGH) return blockedDrafts(); else callLlm()`. This is the most important safety change in this spec |
| `AuditLogPort.java` | Change signature from `void log(String message)` to `void log(AuditEntry entry)` where `AuditEntry` is a sealed interface. Existing string-style entries become a `TextAuditEntry` variant |
| `ConsoleAuditLogAdapter.java` | Add formatter for `LlmCallAuditEntry` |

### 4.5 Files explicitly NOT touched

- All domain objects (`CustomerProfile`, `EmailThread`, `ReplyDraft`, etc.)
- All existing mock / rule-based adapters (kept as fallback / demo)
- The entire `mcp-server/` subproject (it serves a different role — *exposing* sales data as MCP, while this work *consumes* external MCP)
- `skills/sales-ai/SKILL.md` (only docs may need a one-line note pointing at the new flags)

### 4.6 Size estimate

- New: ~14 Java files, ~1500-1800 LOC
- Modified: 4 existing Java files, ~100 LOC
- Removed: 0
- New non-Java: `mcp-config.json` example, `docs/integrations/{gmail,outlook,anthropic,openai,gemini,local-llm}.md`

---

## 5. Data flow — two end-to-end traces

### 5.1 Scenario A — VIP refund request: risk gate triggers, LLM never called

```
[1] Trigger: cron / scheduler
    java SalesAiCli --email gmail --llm anthropic --db jdbc:postgresql://...

[2] McpClient.spawn("gmail")
    ProcessBuilder("npx", "-y", "@gongrzhe/server-gmail-autoauth-mcp")
    JSON-RPC: initialize → notifications/initialized → tools/list

[3] McpEmailThreadAdapter.listUnread()
    tools/call gmail.search { query: "is:unread" }
    ← 1 email from vip@enterprise.com:
      "I want to cancel my contract effective immediately"

[4] JdbcCustomerContextAdapter.load("vip@enterprise.com")
    SELECT * FROM customers WHERE primary_email = ?
    ← tier=VIP, payment_status=overdue_3mo, complaints_3mo=2,
      mentioned_competitor_recently=true

[5] RuleBasedIntentClassifier.classify(thread)
    keywords: ["cancel", "contract"] → BusinessIntent.CANCELLATION

[6] RuleBasedRiskPolicyAdapter.assess(profile, intent, thread)
    CANCELLATION + tier=VIP + overdue payment + recent complaints
    → RiskLevel.HIGH

[7] AdvisorWorkflow — risk gate (THE INVARIANT)
    if (risk.level() == HIGH) {
        AuditLog.write(TextAuditEntry(
          "drafts blocked by risk gate; "
          + "reason=cancellation+vip+overdue+complaints; "
          + "no LLM call made; data did not leave perimeter"));
        return AdvisorResult.blocked(profile, intent, risk,
          "manager approval required");
    }
    // LlmClient is never invoked on this branch.

[8] AdvisorReportRenderer.render(result)
    ⚠ HIGH RISK — drafts BLOCKED
    Reason: cancellation + VIP + overdue payment + recent complaints
    Action: route to manager for review
    No LLM call made. Customer data did not leave perimeter.
```

This trace proves three things:
- The LLM has no architectural path to high-risk requests
- High-risk customer data is not transmitted to any third party
- The audit log records the block and its reason

### 5.2 Scenario B — Routine status inquiry: LLM is called, audit captures everything

```
[1-5] Same as Scenario A, but...

[6] RuleBasedRiskPolicyAdapter.assess(...)
    STATUS_INQUIRY + Standard tier + paid up
    → RiskLevel.LOW

[7] AdvisorWorkflow — risk gate
    risk.level() != HIGH → proceed to LLM

[8] PromptBuilder.build(profile, thread, intent, risk)
    System prompt (hard-coded text block):
      "You are a senior B2B account manager. Draft 2 reply options:
       A) formal/safe, B) warm/relationship.
       Never auto-send. Never promise refunds, contract changes, or
       legal commitments. Output strict JSON:
       {drafts: [{strategy, subject, body}, {strategy, subject, body}]}"
    User prompt (dynamic):
      Customer: {tier=Standard, account_age=2yr, recent_orders=3, ...}
      Recent thread: {last 3 emails verbatim, 1840 chars}
      Intent: STATUS_INQUIRY
      Risk: LOW
      Customer's question: "When will my order ship?"

[9] LlmReplyDraftAdapter.draft(...)
    AnthropicLlmClient.complete(request)
    POST https://api.anthropic.com/v1/messages
      headers: x-api-key: $ANTHROPIC_API_KEY,
               anthropic-version: 2023-06-01,
               content-type: application/json
      body: {model: "claude-3-5-sonnet-20241022",
             max_tokens: 1024,
             system: "...",
             messages: [{role: "user", content: "..."}]}
    ← {content: [{type: "text", text: "{\"drafts\": [...]}"}],
        usage: {input_tokens: 523, output_tokens: 128}}
    Parse JSON → List<ReplyDraft> (2 entries)

[10] AuditLogPort.write(LlmCallAuditEntry{
       timestamp: 2026-05-07T14:32:18Z,
       provider: anthropic,
       model: claude-3-5-sonnet-20241022,
       prompt_hash: sha256(...),
       prompt_length_chars: 2103,
       response_length_chars: 487,
       input_tokens: 523,
       output_tokens: 128,
       latency_ms: 1840,
       estimated_cost_usd: 0.00385,
       api_key_fingerprint: "sk-ant-...8a2f",
       workflow_step: "draft_reply",
       request_id: "req_a4f1"
     })

[11] AdvisorReportRenderer.render(result)
     Customer: ACME Corp (Standard tier)
     Intent: STATUS_INQUIRY · Risk: LOW · auto-draft allowed
     Drafts (anthropic-claude-3-5-sonnet-20241022, 1840ms, $0.004):
       Option A (formal/safe): "Thank you for reaching out..."
       Option B (warm/relationship): "Hi Sarah, totally hear you..."
     Reviewed by: [pending]    ← manual approval still required
     Audit ID: req_a4f1
```

This trace proves:
- LLM is invoked correctly with full context
- Audit captures provider, model, cost, tokens, latency for every call
- The drafts are still gated by manual review (`Reviewed by: [pending]`)

---

## 6. Configuration

### 6.1 `mcp-config.json` schema (Claude Code-compatible)

```json
{
  "mcpServers": {
    "gmail": {
      "command": "npx",
      "args": ["-y", "@gongrzhe/server-gmail-autoauth-mcp"],
      "env": {}
    },
    "outlook": {
      "command": "uvx",
      "args": ["mcp-server-outlook"],
      "env": {}
    }
  }
}
```

Lookup precedence:
1. `--mcp-config <path>` (explicit override)
2. `./mcp-config.json` (project-local)
3. `~/.config/claude/claude_desktop_config.json` (Claude Code user config — interop)
4. Error if `--email gmail|outlook` was passed but no config found.

### 6.2 Environment variables

| Variable | Purpose | Required when |
|---|---|---|
| `ANTHROPIC_API_KEY` | Claude API | `--llm anthropic` |
| `OPENAI_API_KEY` | OpenAI / OpenAI-compatible | `--llm openai` (always); `--llm openai-compatible` (optional — local LLM may not require auth) |
| `GEMINI_API_KEY` | Google Gemini | `--llm gemini` |

### 6.3 CLI flags (full set)

```
java SalesAiCli [options]

Customer source:
  --customer-source mock|json|jdbc        (default: mock)
  --db <jdbc-url>                          (with jdbc)
  --db-user <user>
  --db-password <password>

Email source (Phase 2):
  --email mock|gmail|outlook              (default: mock)
  --mcp-config <path>                      (override default lookup)

LLM provider (Phase 4):
  --llm template|anthropic|openai|gemini|openai-compatible  (default: template)
  --llm-model <model-id>                   (provider-specific default if omitted)
  --llm-endpoint <url>                     (only with openai-compatible — for local LLM)

Workflow:
  --customer <customer-id>                 (process specific customer)
  --since <ISO-8601>                       (only emails after this time)
```

### 6.4 Default models per provider

| Provider | Default model | Reason |
|---|---|---|
| anthropic | `claude-3-5-sonnet-20241022` | Best at structured JSON output and instruction following for safety rules |
| openai | `gpt-4o-2024-08-06` | Structured output mode + cost balance |
| gemini | `gemini-1.5-pro-002` | Long context handling for multi-turn threads |
| openai-compatible | (no default — `--llm-model` required) | Local model name has no single default |

---

## 7. Error handling

| Error | Source | Handling |
|---|---|---|
| MCP server spawn failure | `McpClient.spawn` | Print child stderr + exit code, exit non-zero |
| MCP `initialize` timeout (10s) | JSON-RPC handshake | Kill child process, exit non-zero |
| `tools/call` returns JSON-RPC error | `McpEmailThreadAdapter` | Write audit entry, return empty email list with warning (do not crash) |
| LLM API 4xx (auth/quota) | `*LlmClient.complete` | **Do not fall back to template** (per Q7 decision). Write audit, exit non-zero with clear message |
| LLM API 5xx / network timeout | `*LlmClient.complete` | Exponential backoff retry 3x (1s/2s/4s), then exit non-zero |
| LLM returns unparseable JSON | `LlmReplyDraftAdapter` | Write audit (raw response included), retry once with stricter system-prompt reminder. If still fails, fall back to **template** + warning (avoid blocking user when LLM is unstable) |
| MCP child process zombie | shutdown hook | JVM shutdown hook destroys all spawned processes |
| Risk gate triggers | `AdvisorWorkflow` | **Not an error** — normal path. Produce blocked drafts, exit code 0, route to manager |
| Customer ID not found | `JdbcCustomerContextAdapter` | Existing behavior — throw `CustomerNotFoundException`, CLI prints error |

Key principle: an LLM auth failure (4xx) is a configuration bug and must fail loudly. A garbled LLM response is provider instability and must degrade gracefully. These are different.

---

## 8. Testing strategy

| Layer | Scope | Mechanism | Goal |
|---|---|---|---|
| Unit | Each `LlmClient` | JDK `HttpServer` mock + assertions | HTTP request shape correct, response parsing correct |
| Unit | `PromptBuilder` | Hard-coded fixtures | Prompt content includes all required fields, excludes redacted PII |
| Unit | `AdvisorWorkflow` risk gate | Inject mock `RiskPolicyPort` returning HIGH; spy on `LlmClient` | **Verify LlmClient is never called when risk is HIGH** — most important invariant test |
| Unit | `McpClient` | `ProcessBuilder` running a tiny echo-back JSON-RPC subprocess (shell script in `src/test/resources/`) | initialize handshake + tools/call logic |
| Integration | `McpEmailThreadAdapter` against real Gmail MCP | npm install + manual one-time OAuth, test mailbox fixture | Run in CI when secret is present |
| Integration | Each `LlmClient` against real provider API | Requires API key; only runs on PRs tagged `[integration]` | Confirms provider API has not changed |
| End-to-end | Full `SalesAiCli --email gmail --llm anthropic --db ...` | Shell script with test account | Happy path + risk-blocked path |
| Manual QA | Run 5 realistic email styles, evaluate LLM draft quality | Subjective but mandatory before ship | Catch prompt-quality regressions |

Coverage target: 80%+ for core (workflow, risk gate, each LlmClient, PromptBuilder). Adapter wiring not strictly required.

---

## 9. Documentation deliverables

In addition to code, this work produces:

| File | Purpose |
|---|---|
| `docs/integrations/gmail.md` | npm install, OAuth flow, troubleshooting |
| `docs/integrations/outlook.md` | uvx install, OAuth, troubleshooting |
| `docs/integrations/anthropic.md` | API key acquisition, model selection, cost notes |
| `docs/integrations/openai.md` | Same |
| `docs/integrations/gemini.md` | Same |
| `docs/integrations/local-llm.md` | Ollama / vLLM / llama.cpp setup, recommended models for B2B drafting, **performance vs. cloud comparison** |
| README updates | Honest deployment story: "Java engine zero deps, but Phase 2 deployment needs Node.js for MCP servers"; "When using cloud LLM, customer data leaves your perimeter — use `--llm openai-compatible` to stay local" |
| `mcp-config.json.example` | Working example users can copy-rename |

---

## 10. Out of scope (explicit)

These are deliberately excluded from this spec:

- **Phase 3 — CRM integration** (Salesforce, HubSpot). Future spec.
- **Phase 5 — Cross-channel** (LinkedIn, WhatsApp, LINE OA, Slack). Future spec.
- **Phase 6 — Autonomous closing.** Hard architectural change; not yet.
- **Phase 7 — Spring Boot service wrapper.** Future; needs its own spec on deployment shape.
- **Streaming LLM responses.** Cron / batch use case does not benefit; adds parsing complexity.
- **Token cost optimization** (prompt caching, context trimming, etc.). v2 concern.
- **Fine-tuned / company-specific models.** Configuration-only escape hatch via `--llm-model` is provided; training is not in scope.
- **PII redaction before LLM call.** Important for some industries; deferred to its own spec because it requires a configurable rule engine.

---

## 11. Open questions deferred to implementation plan

These do not block design approval but will be resolved during `writing-plans`:

- Exact backoff strategy for LLM retries (jittered exponential? full jitter?)
- Whether `LlmCallAuditEntry` writes are synchronous or buffered
- Test fixtures for each LLM provider (canned responses for unit tests)
- Whether to publish `mcp-config.json.example` as `.gitignored` or committed

---

## 12. Acceptance criteria

This work is "done" when all of these are true:

1. `java SalesAiCli --email gmail --llm anthropic --db jdbc:sqlite:./customers.db` runs end-to-end against a real Gmail inbox + real Anthropic API + a seeded SQLite DB and produces 2 reply drafts for a low-risk email.
2. The same command, against an email that triggers the risk gate (refund + VIP), produces blocked drafts **without making any LLM API call** (verifiable in audit log).
3. `--llm openai-compatible --llm-endpoint http://localhost:11434/v1 --llm-model llama3.1:70b` runs end-to-end against a local Ollama instance.
4. Audit log includes one `LlmCallAuditEntry` per LLM call with all 13 fields populated.
5. Existing `make seed-and-run` (mock mode) still works — no regression in 60-second demo.
6. All four README languages (en/zh-TW/ja/ko) document the new flags and the data-perimeter caveat.
7. Unit tests pass; integration tests pass when API keys are provided.
