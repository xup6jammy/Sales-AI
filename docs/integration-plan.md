# Integration Plan

A phased migration plan from the MVP shipped in this repo to a production-grade agent. Each phase replaces one or two ports without moving the workflow or the safety rules. The order below is the order we expect to execute; phases can run in parallel where they touch different ports.

The port-to-MCP mapping table in [`architecture.md`](./architecture.md) is the index. The safety rules in [`safety-rules.md`](./safety-rules.md) survive every phase unchanged.

## Phase 1 — MVP (this repo)

Status: done.

- Mock adapters for every port. JSON sample data on disk.
- Rule-based `IntentClassifier` and `ToneClassifier`.
- Constant-list `RiskPolicy`.
- Template-based `ReplyDraftAdapter` with two registers and two languages (en, zh-TW).
- Console audit log via `InMemoryAuditLogAdapter`.
- CLI: `--approve`, `--customer-profile`, `--email-thread`.
- Zero third-party dependencies. Stock JDK 21.

What is intentionally NOT in Phase 1: any code path that reads real mail, sends real mail, authenticates against any provider, calls any LLM, or persists anything outside the audit log.

## Phase 2 — Real email

Replace `MockEmailThreadAdapter` with a real provider adapter, while preserving scoped reads (safety rule 3).

**Code changes**

- New module / adapter: `GmailEmailThreadAdapter` and / or `OutlookEmailThreadAdapter`. Each implements `EmailThreadPort.loadThread(customerId, threadId)` and nothing else.
- New helper: a small mapping table from `customerId` (the agent's view) to the provider's mailbox / message store identifiers. The mapping is resolved before the API call and never returned to the rest of the agent.
- New build profile: real adapters live in a separate Maven / Gradle module from the MVP build, so the MVP cannot accidentally inherit them.

**Ports affected**

- `EmailThreadPort` — implementation only; interface unchanged.

**OAuth scopes (Gmail)**

We request only what we need for thread-scoped reads:

- `https://www.googleapis.com/auth/gmail.readonly` — read messages and threads. We use this only with a specific `threadId` we already know; we never call `users.messages.list` with a broad query.

We deliberately do NOT request:

- `https://www.googleapis.com/auth/gmail.modify` — would allow labelling, archiving, marking read.
- `https://www.googleapis.com/auth/gmail.send` — would allow sending. See safety rule 1.
- `https://mail.google.com/` — full access. Always wrong for an MVP-style integration.

**OAuth scopes (Outlook / Microsoft Graph)**

- `Mail.Read` — read messages and threads (delegated permission, scoped to the signed-in account).

We deliberately do NOT request `Mail.ReadWrite`, `Mail.Send`, `Mail.Send.Shared`, or anything tenant-wide.

**New safety considerations**

- Token storage: tokens live in an OS keychain (Windows Credential Manager / macOS Keychain / `libsecret`), not in environment variables and not on disk in plaintext. The MVP build still has no token storage at all; this is added only in the Phase 2 module.
- Provider rate limits: the adapter must surface 429 / quota errors as a typed error, not a silent retry, so the audit log records the failure honestly (safety rule 8).
- Cross-customer isolation: integration tests verify that calling `loadThread(customerA, threadOfCustomerB)` returns an error, not customer B's thread.

## Phase 3 — Real CRM

Replace `MockCustomerContextAdapter` with a CRM MCP adapter and, eventually, a Text2SQL path on a customer database. The conceptual reference is Agents-Flex's Smart Data Query module.

**Code changes**

- New adapter: `CrmMcpCustomerContextAdapter` implementing `CustomerContextPort.loadProfile(customerId)`. Talks to whichever CRM MCP server the team standardises on (HubSpot-style, Salesforce-style, pipedrive-style — the port does not care).
- A second adapter for write paths: `CrmMcpCrmAdapter` implementing `CrmPort.recordInteraction`. The MVP exercises only this single write; Phase 3 keeps that constraint.
- Optional: a `Text2SqlCustomerContextAdapter` for cases where the system of record is a SQL database rather than a CRM. The Text2SQL contract returns the same `CustomerProfile` record so callers do not change.

**Ports affected**

- `CustomerContextPort` — implementation only.
- `CrmPort` — implementation only.

**New safety considerations**

- Field-level redaction at the source: `CustomerProfile` includes internal-only fields (lifetime value, internal notes). The Phase 3 adapter is the right place to enforce role-based visibility, before the data even enters the agent.
- Read-mostly: Phase 3 keeps `CrmPort` to one write method (`recordInteraction`). Adding `updateDealStage` or `closeDeal` is a Phase 5+ concern and would require a separate approval path.
- Caching: profile reads can cache for the duration of one CLI invocation. Caching across invocations is out of scope.

## Phase 4 — Real LLM drafts

Replace `TemplateReplyDraftAdapter` with an LLM-backed adapter, using an Agents-Flex Skill as the orchestration layer (or a direct SDK call against Claude / Bedrock / a local LLM, depending on deployment).

**Code changes**

- New adapter: `LlmReplyDraftAdapter` implementing `ReplyDraftPort.compose`. Internally it calls an Agents-Flex Skill that knows the two registers (safe / formal, warm / relationship) and the two languages (en, zh-TW).
- Adapter still receives only `CustomerDraftView`, never the full `CustomerProfile`. Safety rule 6 is enforced at the type level, not by the adapter remembering to redact.
- Output validation: the adapter returns exactly two `ReplyDraft` instances or fails. No "the model only produced one draft this time" silent fallback.

**Prompt caching**

The reply prompt has a stable system prompt (skill instructions + register / tone guide), a stable customer-profile preamble per invocation, and a small thread-summary tail. The system prompt and the redacted customer view are good cache candidates: they are reused across both register variants and across re-runs within the same session. The thread summary is short and changes per email; it should be at the end of the prompt so the cache prefix stays stable.

If the underlying SDK supports explicit cache breakpoints (Anthropic prompt caching), set them after the system prompt and after the customer view. Do not cache anything that contains the actual draft outputs.

**Ports affected**

- `ReplyDraftPort` — implementation only.

**New safety considerations**

- Output schema: drafts MUST be validated against a structured schema before reaching the renderer. Free-form model output is rejected.
- Refusal handling: if the model refuses (correctly) — for example, if the user has somehow piped in content that asks for legally-binding language — the adapter surfaces the refusal to `RiskPolicyPort` for re-evaluation rather than silently producing a weaker draft.
- Privacy: re-confirm at this phase that no internal-only profile field is part of the prompt. Add an integration test that diffs the rendered prompt against an allowlist of fields.

## Phase 5 — Approval routing

Replace `ManualApprovalAdapter` (which today only reads `--approve` from CLI flags) with a real approval routing path: a Slack approval bot, or a ticketing-system integration (Jira, Linear, ServiceNow).

**Code changes**

- New adapter: `SlackApprovalAdapter` implementing `ApprovalPort.isApproved`. The approval flow is: when `RiskDecision.requiresManagerApproval` is true, post a structured message to a designated Slack channel with the report (drafts shown but unmistakeably blocked), an `Approve` button, and a `Reject` button. The adapter blocks (with a configurable timeout) until a button is pressed.
- Approval message is also written to the audit log with the approver's identity and the timestamp.
- Optional fallback adapter: `TicketingApprovalAdapter` opens a ticket in the configured system, polls until the ticket reaches a designated status, then resolves.

**Approval message shape**

```
[Sales Advisor — Manager Approval Required]

Customer: <displayName> (<customerId>)
Tier: <tier>
Risk decision: REQUIRES_MANAGER_APPROVAL
Triggers: <comma-separated trigger names>
Rationale: <one paragraph>

Draft A (Safe / Formal):
> <quoted body>

Draft B (Warm / Relationship-Focused):
> <quoted body>

[Approve]  [Reject]
```

**Ports affected**

- `ApprovalPort` — implementation only.
- `AuditLogPort` — receives one extra entry per approval decision (approver, decision, latency).

**New safety considerations**

- Approval cannot be self-granted: the requester (the salesperson running the agent) cannot also be the approver. The adapter checks identity against an authorised-managers list.
- Approval is per-thread, not standing: an approval clears one report. Re-running the workflow on the same thread requires a new approval.
- Approval still does not send mail. Safety rule 1 is intact.

## Phase 6 — Knowledge base / RAG

Plug a vector store of past won / lost playbooks behind a new port, used by the reply composer to ground its drafts in proven plays.

**Code changes**

- New port: `PlaybookPort` with method `List<PlaybookSnippet> retrieve(Intent, Tone, CustomerProfile)`. This is a NEW port, not a replacement.
- New adapter: `VectorStorePlaybookAdapter` against the chosen store (pgvector, Qdrant, Weaviate, etc).
- `LlmReplyDraftAdapter` from Phase 4 is updated to call `PlaybookPort` and include retrieved snippets as additional grounding. Snippets are also subject to the redaction in safety rule 6.

**Ports affected**

- New: `PlaybookPort`.
- Updated wiring: `app` passes the new port into the reply composer.

**New safety considerations**

- Provenance: every snippet retrieved must carry its source (which deal, which playbook version, who recorded it). The audit entry for the retrieval lists the sources.
- Stale playbooks: snippets older than a configurable threshold (default 18 months) are demoted or filtered. Sales playbooks rot.
- Customer leakage: snippets MUST be redacted of customer-identifying information before they enter the prompt, even if they are about a different customer than the current one.

## Phase 7 — Spring Boot service + expose as MCP tool

Wrap the workflow in a Spring Boot service and expose it as its own MCP server. Other Claude Code skills can then call this advisor as a single tool, completing the inversion: the Java engine becomes the MCP server it was always shaped to be.

**Code changes**

- Add Spring Boot to a new `service` module. The MVP build remains zero-dep and shippable on its own.
- Implement an MCP server interface around `AdvisorWorkflow`. The MCP tool exposes a single method `analyseCustomerThread(customerId, threadId, approve)` and returns the rendered `AdvisorReport`.
- DI is now Spring-managed. Hand-rolled wiring stays in the MVP `app` package; the service module is wired via Spring configuration classes.
- Add a health-check endpoint and a structured-log appender for `AuditLogPort` (e.g. JSON lines suitable for Splunk or OpenTelemetry).

**Ports affected**

- All ports gain Spring-bean implementations alongside their MVP versions.
- `AuditLogPort` likely upgrades from in-memory to a structured logger / OpenTelemetry exporter.

**New safety considerations**

- The MCP boundary is a trust boundary. The service authenticates incoming MCP requests; it does not assume the caller is on the same machine.
- The MCP tool description is surfaced to the calling Claude Code instance. It must restate the safety rules — especially the no-send rule and the approval gate — so the calling agent does not misuse it.
- Rate limits: at this phase, the agent is callable by other agents. A misbehaving caller could loop. The service applies a per-customer-per-minute limit and surfaces 429s honestly to the audit log (safety rule 8).
