# Sales AI — Design Spec

**Date:** 2026-05-06
**Status:** Approved — proceeding to implementation
**Owner:** project owner (this repo)

## What we are building

A **Sales / Account Manager Email Copilot** that:

1. Understands customer context before replying.
2. Reads only relevant email threads (never the whole inbox).
3. Classifies business intent and emotional tone.
4. Evaluates business risk against an explicit policy.
5. Produces a reply strategy and two draft options (safe/formal + warm/relationship).
6. Requires human approval for any sensitive action.
7. Leaves an audit trail for every tool-like step.

## What we are *not* building

- A general chatbot.
- An autonomous email sender. The agent does not click "send" and does not silently create drafts in mailboxes.
- A replacement for the salesperson. It is a copilot.
- A bulk-outbound tool.

## Architectural premise: the SKILL is the agent

```
Claude Code  →  reads  →  skills/sales-ai/SKILL.md
                                       │
                                       │ orchestrates
                                       ▼
                       Tool layer (replaceable per phase)
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        ▼                              ▼                              ▼
   MVP today:                   Phase 2:                       Phase 3:
   Java CLI                     + Gmail / Outlook MCP          + CRM MCP, Text2SQL,
   (this repo)                  + Agents-Flex Skills           + RAG knowledge base
```

The Java MVP is a **reference implementation and smoke test** of the workflow.
The user-facing agent lives in `SKILL.md`, which Claude Code follows.
Replacing the engine (Java CLI → MCP servers) does not change the workflow
or the safety rules in `SKILL.md`.

## Tech constraints (MVP)

- Java 21, plain `javac` and `java`.
- No external dependencies. No Maven, no Gradle, no LLM SDKs.
- No real Gmail / Outlook / CRM credentials.
- No tests in the build pipeline (out of MVP scope; documented as future work).
- Cross-platform: must compile on Linux/macOS bash and Windows PowerShell with the same source.

## Hexagonal layout

```
com.example.salesai
├── SalesAiCli          entry point + CLI argument parsing
├── domain                   immutable records (no logic, no I/O)
├── ports                    interfaces — every future MCP integration point
├── adapters                 mock / in-memory implementations of ports
├── classify                 keyword-based intent + tone classifiers
├── risk                     rule-based risk evaluator (used by adapter)
├── reply                    template-based draft generator (used by adapter)
└── app                      AdvisorWorkflow + AdvisorReportRenderer
```

### Ports (each maps to a future MCP tool / Agents-Flex skill)

| Port | Future replacement |
|------|--------------------|
| `CustomerContextPort` | CRM MCP server, Text2SQL on customer DB |
| `EmailThreadPort` | Gmail MCP / Outlook MCP / IMAP MCP |
| `RiskPolicyPort` | Policy engine, rules service, eventually LLM with structured output |
| `ReplyDraftPort` | LLM via Agents-Flex Skill (Claude / Bedrock / etc.) |
| `CrmPort` | CRM MCP write operations (notes, tasks, stage updates) |
| `ApprovalPort` | Slack approval bot, ticketing system, manager UI |
| `AuditLogPort` | OpenTelemetry, Splunk, internal audit DB |

## Domain model (records)

`CustomerProfile`, `CommercialHistory`, `EmailMessage`, `EmailThread`,
`BusinessIntent` (enum), `EmotionalTone` (enum), `RiskLevel` (enum),
`RiskAssessment`, `ReplyStrategy`, `ReplyDraft`, `FollowUpAction`,
`AdvisorRequest`, `AdvisorResult`.

Records are immutable, contain no behaviour beyond accessors and convenience
constructors. All business logic lives in the workflow / classifiers / risk
engine, not on the data classes.

## Workflow (the 11 steps)

1. Identify customer by email address (`CustomerContextPort.findByEmail`).
2. Load customer profile and commercial history.
3. Load recent email thread for that customer (`EmailThreadPort.loadLatestForCustomer`).
4. Classify business intent (`RuleBasedIntentClassifier`).
5. Detect emotional tone (`RuleBasedIntentClassifier`).
6. Evaluate risk (`RiskPolicyPort`).
7. Decide reply strategy.
8. Generate two drafts (`ReplyDraftPort`):
   - **Option A:** safe / formal
   - **Option B:** warm / relationship-focused
9. Recommend follow-up actions.
10. Print structured advisor report.
11. Write audit log entries for each tool-like action above.

The entire flow is orchestrated in `app.AdvisorWorkflow.run(AdvisorRequest)`
and produces an `AdvisorResult`. Rendering is separate, in
`app.AdvisorReportRenderer`.

## Business intent classification

Deterministic keyword-based classification over subject + body, scored per
intent, with `UNKNOWN` as the floor. Supported intents:

`INQUIRY`, `QUOTATION`, `COMPLAINT`, `RENEWAL`, `PAYMENT_ISSUE`,
`DELIVERY_DELAY`, `TECHNICAL_SUPPORT`, `NEGOTIATION`, `CHURN_RISK`, `UNKNOWN`.

Bilingual keywords (English + 繁體中文) so the demo handles realistic
Asia-Pacific B2B mail.

A future LLM classifier can replace this class behind the same call signature.

## Emotional tone

Light keyword + cue scoring across:
`NEUTRAL`, `FRUSTRATED`, `ANGRY`, `URGENT`, `APPRECIATIVE`, `CONFUSED`.

## Risk policy

Rule-based scoring. Triggers and severities:

| Trigger | Risk |
|--------|------|
| refund / 退款 | HIGH |
| legal / 法務 / lawsuit | HIGH |
| contract change / 合約變更 | HIGH |
| exceptional discount / 折扣特批 | HIGH |
| payment overdue + VIP | MEDIUM |
| delivery delay (single) | LOW–MEDIUM |
| churn / cancellation / 解約 | REQUIRES_MANAGER_APPROVAL |
| angry tone + VIP | MEDIUM |
| open high-priority support ticket | MEDIUM |

Any of the following force `REQUIRES_MANAGER_APPROVAL`:

- refund / credit request
- legal language
- contract amendment or termination
- exceptional discount
- credible cancellation / churn signal

## Approval gate

`ManualApprovalAdapter` defaults to **denied — pending manager approval**.
The CLI flag `--approve` simulates a granted approval and is recorded in the
audit log. **No email is ever sent**, regardless of approval state, in MVP.

## CLI surface

```
java -cp out com.example.salesai.SalesAiCli                # bundled demo
java -cp out com.example.salesai.SalesAiCli --help
java -cp out com.example.salesai.SalesAiCli --approve      # simulate approval
java -cp out com.example.salesai.SalesAiCli \
    --customer-profile path/to/customer.json \
    --email-thread path/to/thread.json
```

The CLI loads the bundled JSON samples by default (from
`samples/customer-profile.json` and `samples/email-thread.json`). The path
flags allow swapping in your own data without changing code.

A minimal hand-rolled JSON parser (`adapters/MiniJson.java`) handles the
sample files. No external JSON dependency.

## Output format

Exactly the structure given in the project brief:
`Customer Context → Email Summary → Risk Assessment → Recommended Reply
Strategy → Draft Option A → Draft Option B → Follow-Up Actions → Audit
Summary`.

## Audit log

`ConsoleAuditLogAdapter` writes timestamped events to stdout (separate
section in the report). Events: customer lookup, email thread read, intent
classification, tone detection, risk evaluation, draft generation, approval
requirement, approval grant (if `--approve`).

## File deliverables

- `README.md` — GitHub landing page
- `LICENSE` — MIT
- `.gitignore` — Java + IDE
- `docs/architecture.md` — hexagonal layout, port → MCP mapping
- `docs/safety-rules.md` — every red line, codified
- `docs/integration-plan.md` — phased migration to Agents-Flex / MCP / Spring Boot
- `docs/borrowed-patterns.md` — what we learned from each reference repo and why we did not vendor any code
- `skills/sales-ai/SKILL.md` — the agent definition
- `samples/customer-profile.json`, `samples/email-thread.json`, `samples/advisor-output.md`
- `src/main/java/...` — Java 21 sources

## Acceptance criteria

- `javac` builds the project on Java 21 with no warnings escalated to errors.
- `java -cp out com.example.salesai.SalesAiCli` prints the full
  spec'd report end to end.
- HIGH and `REQUIRES_MANAGER_APPROVAL` paths visibly block draft delivery.
- No real email is sent. No real credentials are present.
- README clearly explains future Agents-Flex / MCP integration.
- No source code is copied from any referenced repository.
