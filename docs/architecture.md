# Architecture

This document describes how the Sales AI is laid out internally, why it is laid out that way, and where the seams are for replacing pieces with real services in later phases.

The architectural premise is on the front page of the README and bears repeating here: **the Skill is the agent, the Java MVP is the engine, replaceable per phase.** The workflow defined in `skills/sales-ai/SKILL.md` is the spine. Today the engine is one Java CLI. Tomorrow it is a set of MCP servers. The skill does not change.

## Hexagonal layout

The codebase follows a hexagonal (ports and adapters) layout. The intent is that the domain logic — what an account manager needs to know to reply to a customer — does not depend on any of the things that change (Gmail vs Outlook, rule-based vs LLM classifier, manual approval vs Slack bot).

```
com.example.salesai
├── domain        // pure data: records, enums, no IO, no logging
├── ports         // interfaces only: what the use-case needs from the world
├── adapters      // mock implementations of every port for the MVP
├── classify      // intent + tone classification (rule-based today)
├── risk          // risk policy: maps (intent, tone, context) → decision
├── reply         // template-based reply composer (two registers)
└── app           // wiring + CLI: orchestrates the 11-step workflow
```

What each package owns and what it must NOT know:

- **`domain`** owns the record types: `CustomerProfile`, `EmailThread`, `EmailMessage`, `Intent`, `Tone`, `RiskDecision`, `ReplyDraft`, `AdvisorReport`, etc. It must NOT know about JSON, files, the CLI, or any port.
- **`ports`** owns the interfaces: `CustomerContextPort`, `EmailThreadPort`, `RiskPolicyPort`, `ReplyDraftPort`, `CrmPort`, `ApprovalPort`, `AuditLogPort`. It must NOT know about any concrete adapter, classifier, or reply template.
- **`adapters`** owns the mock implementations. Each adapter implements exactly one port and reads JSON from disk. It must NOT depend on `classify`, `risk`, `reply`, or `app`.
- **`classify`** owns the rule-based intent and tone classifiers. It depends only on `domain`. It must NOT do IO. Replacing it with an LLM is a swap of one class.
- **`risk`** owns the policy that maps `(intent, tone, profile)` to `RiskDecision`. It depends only on `domain` and reads no external state. The trigger list (refund, legal, contract, exceptional discount, cancellation, churn-on-VIP) lives here as constants.
- **`reply`** owns the template-based composer. It receives a redacted view of the profile (see safety rule 6) and emits two `ReplyDraft` objects. It must NOT see internal-only profile fields.
- **`app`** owns wiring and the CLI. This is the only package that knows about every other package. It is intentionally thin: it builds the adapter graph, runs the 11-step workflow, and renders the report.

## No DI framework — intentional

This project ships with zero third-party dependencies. There is no Spring, no Guice, no Dagger, no service-loader trick. Wiring lives in one method in `app`, by hand.

This is a deliberate constraint. The MVP is meant to be small enough to read in an afternoon, build with a stock JDK, and reason about without a runtime container. Phase 7 of the integration plan introduces Spring Boot, but only when the agent is exposed as an MCP server and the dependency is actually paying for itself.

The cost of doing it by hand is one wiring method. The benefit is that the entire object graph fits on one screen and there is nothing reflective happening at startup.

## The 11-step workflow

The CLI in `app` runs exactly these eleven steps, in this order, every time. Each step names the port that performs the work. The same workflow is described in user-facing language in `skills/sales-ai/SKILL.md`.

| # | Step | Port |
|---|------|------|
| 1 | Identify the customer (CLI args or interactive prompt). | — |
| 2 | Load customer context (tier, contract, payment, orders, tickets, notes). | `CustomerContextPort.loadProfile` |
| 3 | Load the relevant email thread for that customer. | `EmailThreadPort.loadThread` |
| 4 | Summarise the thread (factual, no interpretation). | local — derived from step 3 |
| 5 | Classify business intent (one of ten enum values). | `classify.IntentClassifier` |
| 6 | Classify emotional tone (six options). | `classify.ToneClassifier` |
| 7 | Evaluate risk against policy. | `RiskPolicyPort.evaluate` |
| 8 | Decide reply strategy (one or two sentences). | local — composes (intent, tone, risk) |
| 9 | Generate two reply drafts (safe / formal, warm / relationship). | `ReplyDraftPort.compose` |
| 10 | Surface approval gate; block drafts if required. | `ApprovalPort.requirementFor` |
| 11 | Render report and audit summary; optionally record interaction. | `CrmPort.recordInteraction`, `AuditLogPort.dump` |

Every port call writes one line to the audit log at the moment it returns. Step 11's audit summary is just a render of those lines.

## Port to MCP mapping

The whole point of the port layer is that each port maps cleanly to an MCP server (or set of servers). The roadmap is fixed by these mappings.

| Port | Status today | Future replacement |
|------|--------------|--------------------|
| `CustomerContextPort` | ✅ JDBC adapter + SQL MCP server (this repo) | Real CRM via Text2SQL |
| `EmailThreadPort` | Mock JSON | Gmail MCP / Outlook MCP / IMAP MCP |
| `RiskPolicyPort` | Rule-based Java | Policy engine, eventually LLM with structured output |
| `ReplyDraftPort` | Templates | LLM via Agents-Flex Skill |
| `CrmPort` | No-op | CRM MCP write operations |
| `ApprovalPort` | CLI flag | Slack approval bot, ticketing system |
| `AuditLogPort` | Console | OpenTelemetry, Splunk, internal audit DB |

Each phase of [`integration-plan.md`](./integration-plan.md) replaces one or two of these ports. The other packages do not move.

## Database layer (shipped in Phase 3a)

`CustomerContextPort` has two implementations today, picked by command-line flag:

| Adapter | When to use | Where it reads from |
|---|---|---|
| `MockCustomerContextAdapter` | Default; demos and tests | `samples/customer-profile.json` |
| `JdbcCustomerContextAdapter` | `--db jdbc:...` | `customers` / `orders` / `support_tickets` / `customer_notes` tables |

The schema lives in [`mcp-server/schema/`](../mcp-server/schema) — one DDL file per dialect (SQLite, MySQL, Postgres). All three are deliberately ANSI-compatible, so the adapter and MCP server use one set of `?`-bound queries across all three databases. The JDBC URL prefix selects the dialect; no per-database code path is needed yet.

The JDBC adapter is the engine's path to a real database. It does not go through MCP — that would add two extra hops between two pieces of code that already share the `domain` types. Direct JDBC is the right choice when the consumer is in-process.

## SQL MCP server (shipped in Phase 3a)

The MCP server is a separate sub-project at [`mcp-server/`](../mcp-server). It speaks JSON-RPC 2.0 over stdin/stdout (the transport Claude Code uses for spawned servers) and exposes four whitelisted SQL-backed tools:

```
┌──────────────┐  stdio JSON-RPC  ┌─────────────────────┐  JDBC  ┌──────────┐
│ Claude Code  │ ───────────────▶ │ SalesMcpServer      │ ─────▶ │ SQLite / │
│ (the skill)  │ ◀─────────────── │  4 whitelisted tools│        │ MySQL /  │
└──────────────┘                  └─────────────────────┘        │ Postgres │
                                                                  └──────────┘
```

| Tool | SQL it runs |
|---|---|
| `customer.findByEmail(email)` | `SELECT ... WHERE LOWER(primary_email) = LOWER(?)` |
| `customer.findById(customerId)` | `SELECT ... WHERE id = ?` |
| `customer.listOrders(customerId, limit?)` | `SELECT ... WHERE customer_id = ? ORDER BY ordered_on DESC LIMIT ?` |
| `customer.listOpenTickets(customerId)` | `SELECT ... WHERE customer_id = ? AND status = 'OPEN'` |

There is **no generic `runSql(query)` tool**, by design. The whitelist is the boundary: the LLM can supply parameter values, never SQL fragments. Adding a new tool is a code change with a code review. This is how the "scoped reads" promise from `SKILL.md` survives prompt injection in inbound customer email.

The server is intentionally minimal — protocol layer, JDBC layer, four tools, one entry point. No external MCP SDK; the protocol fits in 80 lines of Java. Driver jars are not committed (license, size, choice); see [`mcp-server/lib/README.md`](../mcp-server/lib/README.md). Full design rationale lives in [`mcp-server.md`](./mcp-server.md).

## Why records

The domain types are Java 21 records.

- **Immutability** — records are final and their fields are final. The data passed between steps cannot be mutated by a downstream step. This rules out a class of bugs where step 9 quietly edits the profile that step 7 already evaluated.
- **Equality and hashing for free** — `equals`, `hashCode`, and `toString` are generated. Tests can compare expected and actual reports with `==` semantics on value.
- **No annotation magic** — there is no Lombok in the build, no annotation processor, no compile-time bytecode trick. What you see in the source is what runs.
- **Fits the data model** — the domain is genuinely value-shaped: a profile is a profile, a draft is a draft. Behaviour lives in the policy, classifier, and composer classes, not on the data.

## Why a hand-rolled JSON parser

The MVP includes a small JSON reader in `adapters` that handles the subset of JSON the sample files actually use: objects, arrays, strings, numbers, booleans, null, with no streaming and no comments.

- **Zero dependencies** — the README badge "No dependencies" stops being true the moment Jackson or Gson lands in the build. The cost of that badge is one parser class. We pay it.
- **Bounded surface** — the parser only needs to read sample profile and thread files. It does not need to round-trip arbitrary JSON, support `@JsonAlias`, or handle dates. Bounded scope means a small implementation.
- **No reflection** — the parser hands back `Map<String,Object>` and `List<Object>`; the adapter explicitly extracts each field by name. This makes the JSON-to-record mapping legible and grep-able. When Phase 2 swaps in a real Gmail adapter, the question "which JSON field maps to which record field?" still has a one-line answer per field.
- **It is not the production answer** — when the project moves to Spring Boot in Phase 7 (or when an adapter actually needs to talk to a complicated API), the right thing is to take Jackson or Gson on. The hand-rolled parser is an MVP-only artefact.

## Extension points

Three extensions are likely to land first. Each one is a single-class swap thanks to the port layer.

- **Replace the rule-based classifier with an LLM.** The classifier today returns the highest-scoring intent based on keyword and pattern matching. To swap in an LLM, write a new `IntentClassifier` (and `ToneClassifier`) that calls an LLM with a structured-output schema, returns the same enum values, and respects the same audit contract. No other class moves.
- **Replace the risk policy with an external service.** Today the policy is a constant trigger list and a small switch. To move it to an external service (an internal risk API, a feature flag service, an LLM with policy retrieval), implement `RiskPolicyPort` against that service. The contract — `(intent, tone, profile) → RiskDecision` — does not change.
- **Plug in a real Gmail or Outlook adapter.** Implement `EmailThreadPort` against the provider's API or against an MCP server. The signature is `loadThread(customerId, threadId)`. The adapter is responsible for translating the customer / thread identifiers into provider-specific IDs and for enforcing scoped reads (safety rule 3).

## Source tree

The list below is the planned layout for the `.java` files in `src/main/java/com/example/salesai/`. A separate agent owns the actual code; this document tracks what each file is for.

- `app/SalesAiCli.java` — entry point, argument parsing, builds the adapter graph by hand.
- `app/AdvisorWorkflow.java` — the 11-step workflow, one method per step, called in order.
- `app/AdvisorReportRenderer.java` — formats the final `AdvisorReport` into the section headings the README documents.
- `app/CliOptions.java` — record holding parsed CLI flags (`--approve`, `--customer-profile`, `--email-thread`).
- `domain/CustomerProfile.java` — customer record: tier, contract status, payment status, orders, tickets, notes.
- `domain/CustomerDraftView.java` — redacted projection of `CustomerProfile`, the only view the reply composer can see.
- `domain/EmailThread.java` — thread record: id, subject, customer email, ordered list of messages.
- `domain/EmailMessage.java` — single message record: id, from, to, sentAt, direction, body.
- `domain/Intent.java` — enum: INQUIRY, QUOTATION, COMPLAINT, RENEWAL, PAYMENT_ISSUE, DELIVERY_DELAY, TECHNICAL_SUPPORT, NEGOTIATION, CHURN_RISK, UNKNOWN.
- `domain/Tone.java` — enum: NEUTRAL, FRUSTRATED, ESCALATING, CONCILIATORY, URGENT, FORMAL.
- `domain/RiskDecision.java` — record: level, requiresManagerApproval, triggers, rationale.
- `domain/ReplyDraft.java` — record: register (SAFE_FORMAL or WARM_RELATIONSHIP), language, body.
- `domain/AdvisorReport.java` — record aggregating every step's output for the renderer.
- `domain/AuditEntry.java` — record: timestamp, port, method, args, outcome.
- `ports/CustomerContextPort.java` — interface: `CustomerProfile loadProfile(String customerId)`.
- `ports/EmailThreadPort.java` — interface: `EmailThread loadThread(String customerId, String threadId)`.
- `ports/RiskPolicyPort.java` — interface: `RiskDecision evaluate(Intent, Tone, CustomerProfile)`.
- `ports/ReplyDraftPort.java` — interface: `List<ReplyDraft> compose(CustomerDraftView, EmailThread, Intent, Tone, String strategy)`.
- `ports/CrmPort.java` — interface: `void recordInteraction(String customerId, AdvisorReport report)`.
- `ports/ApprovalPort.java` — interface: `boolean isApproved(RiskDecision decision, CliOptions opts)`.
- `ports/AuditLogPort.java` — interface: `void append(AuditEntry entry); List<AuditEntry> entries();`.
- `adapters/MockCustomerContextAdapter.java` — reads `samples/customer-profile.json` from disk.
- `adapters/MockEmailThreadAdapter.java` — reads `samples/email-thread.json` from disk; thread-scoped.
- `adapters/RuleBasedRiskPolicyAdapter.java` — applies the constant trigger list.
- `adapters/TemplateReplyDraftAdapter.java` — produces two drafts from templates by register and language.
- `adapters/MockCrmAdapter.java` — appends a line to a console-buffered "CRM" log.
- `adapters/ManualApprovalAdapter.java` — reads `--approve` from `CliOptions`; logs the approval.
- `adapters/InMemoryAuditLogAdapter.java` — appends `AuditEntry` to an in-memory list, dumps on demand.
- `adapters/MiniJsonReader.java` — small JSON reader covering only the shapes the samples use.
- `classify/IntentClassifier.java` — rule-based intent classifier, returns highest-scoring `Intent`.
- `classify/ToneClassifier.java` — rule-based tone classifier, returns one `Tone`.
- `risk/RiskPolicy.java` — pure-function policy used by `RuleBasedRiskPolicyAdapter`.
- `risk/RiskTriggers.java` — constants and predicates for the triggers in safety rule 4.
- `reply/ReplyComposer.java` — template selector and assembler used by `TemplateReplyDraftAdapter`.
- `reply/Templates.java` — the actual template strings, keyed by register and language.

When the engine code lands, this list should match what is on disk. Drift is a documentation bug — please file an issue.
