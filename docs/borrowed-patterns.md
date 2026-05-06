# Borrowed Patterns, Not Borrowed Code

This project owes a clear conceptual debt to several open-source projects. None of them ship inside this repo; none of their source code has been vendored, forked, copied, or paraphrased into Java files here. What we did take is the *shape* of certain ideas — how a Skill should look, how an email port should be unified across providers, what writes a CRM agent should and should not be allowed to make.

This document names the references and is specific about what was adopted and what was not. The goal is to make it easy for a reviewer to verify the claim that this is a clean re-implementation against a fresh design, not a derivative work.

## Agents-Flex

Reference: [agents-flex/agents-flex](https://github.com/agents-flex/agents-flex) — a Java agent framework with a Skill abstraction, an MCP module, a Text2SQL framework, and Spring Boot integration.

**Patterns we adopted**

- *Skill-as-spec philosophy.* Agents-Flex treats a Skill as a declarative unit that an LLM consumes. Our `skills/customer-email-sales-advisor/SKILL.md` is shaped the same way: it tells the model what to do, in what order, with what guardrails, and the engine is whatever currently fulfils those tool calls. This is what lets us claim "the Skill is the agent, the engine is replaceable per phase".
- *Port / adapter separation that mirrors how Agents-Flex Skills will plug in later.* When Phase 4 swaps `TemplateReplyDraftAdapter` for an LLM-backed adapter, the natural shape on the other side is an Agents-Flex Skill. Our port signatures were chosen so that drop-in is a single class.
- *Text2SQL as a future shape for `CustomerContextPort`.* Phase 3 of the integration plan explicitly cites Agents-Flex Smart Data Query as the conceptual reference for moving from a CRM API to a SQL-grounded customer view.
- *Spring Boot integration as the eventual deployment shape.* Phase 7 wraps the workflow in Spring Boot and exposes it as an MCP server. Agents-Flex's Spring Boot module is the precedent.

**What we did NOT copy**

- No source file from the Agents-Flex repo lives in this codebase.
- No package layout from Agents-Flex was reused. Our package layout (`domain` / `ports` / `adapters` / `classify` / `risk` / `reply` / `app`) is a hexagonal layout we picked for the MVP; it does not mirror Agents-Flex's package structure.
- No class name from Agents-Flex was reused. Names like `AdvisorWorkflow`, `RiskPolicyPort`, `CustomerDraftView` are local.
- No build dependency on Agents-Flex. The MVP has zero third-party dependencies. Phase 7 introduces Spring Boot; even at that point, Agents-Flex itself is a *reference*, not a build dependency.

## marlinjai/email-mcp

Reference: [marlinjai/email-mcp](https://github.com/marlinjai/email-mcp) — a unified email MCP server that abstracts Gmail, Outlook, iCloud, and IMAP behind a single tool surface.

**Patterns we adopted**

- *Single email port across providers.* `EmailThreadPort` is a one-method interface (`loadThread(customerId, threadId)`) with provider details hidden. The conceptual move — "the agent does not know it is talking to Gmail vs Outlook" — is the same move marlinjai/email-mcp makes at the MCP layer.
- *Thread as the unit of work.* Both projects treat one thread as the unit, not one message and not one mailbox. That choice falls out of safety rule 3 (scoped reads) here; in marlinjai/email-mcp it is a usability and correctness choice for the MCP surface.

**What we did NOT copy**

- We do not ship an MCP server in this repo, period. The Phase 2 plan is to *consume* a server like marlinjai/email-mcp, not to fork it.
- No code from marlinjai/email-mcp lives here. Our `EmailThreadPort` is a Java interface; their tool surface is JSON-shaped MCP tools. They are not the same artefact.
- We do not currently support iCloud or IMAP even in plans; Phase 2 names Gmail and Outlook only. iCloud / IMAP are explicitly out of scope until a customer asks for them.

## Public Gmail MCP server examples

Reference: the family of community-maintained Gmail MCP servers (search thread, read thread, list attachments, create draft, send-after-approval). Several open implementations exist; we treat them as a category, not a single project, because the patterns are consistent across implementations.

**Patterns we adopted**

- *Thread-scoped reads.* No inbox listing. No cross-customer search. The agent looks at one thread it already knows the id of. This is the standard responsible shape used by these servers, and it is also our safety rule 3.
- *Drafts before sends.* The standard pattern in these servers is `createDraft` separate from `sendDraft`, with the latter requiring an explicit user action. We took it further: this MVP has no `sendDraft` at all (safety rule 1).
- *Approval before any write.* Even creating a draft is an approval-gated action in well-behaved Gmail MCP servers. We adopted the same posture for the future write path; the MVP today does not write to any mail provider.

**What we did NOT copy**

- No code, no JSON schemas, no tool descriptions, no prompt text from any specific Gmail MCP example was reused.
- Our future Gmail adapter (Phase 2) will define its own tool surface within `EmailThreadPort` and is not pinned to any specific community schema.
- We do not implement attachment handling, label management, or multi-account mailbox switching in any phase of the current plan. Those exist in many Gmail MCP servers; we deliberately scope smaller.

## Public CRM MCP server examples

Reference: the family of community-maintained CRM MCP servers covering HubSpot, Salesforce, pipedrive, and similar (get deal, update stage, add note, create follow-up task, recordInteraction-style writes).

**Patterns we adopted**

- *Read-mostly write surface.* Public CRM MCP servers tend to expose many read methods and a small number of writes. We mirror that: `CrmPort.recordInteraction` is the only write the MVP exercises.
- *Explicit shapes per write.* Each write in those servers carries a structured payload that names what changed. Our `recordInteraction` takes a typed `AdvisorReport` rather than an arbitrary string, for the same reason.
- *Customer profile is the natural unit.* Public CRM MCP servers tend to expose "get customer / get account / get contact" as the first-class read. Our `CustomerContextPort.loadProfile(customerId)` is the same shape.

**What we did NOT copy**

- No tool schema, no JSON shape, no field name set was lifted from any specific CRM MCP server. Our `CustomerProfile` record is shaped to fit the demo data and the safety / privacy needs of this agent.
- No specific CRM is targeted in the MVP. Phase 3 keeps the CRM choice abstract; the port hides the system of record.
- We do not implement deal-stage transitions, opportunity creation, or quote generation in any phase of the current plan. Those are valid CRM MCP capabilities; they are simply not on this roadmap.

---

*Disclaimer: this project does not redistribute any third-party source code. All references above are conceptual influences. If you believe a specific section of code in this repository was derived from one of these projects without proper attribution, please file an issue and point at the file and line; we will resolve it immediately.*
