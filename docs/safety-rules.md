# Safety Rules

These are the hard rules the Customer Email Sales Advisor lives by. Every rule below is enforced in code, not just in prose. If you can find a path through the codebase that violates one of them, that is a bug — see the closing note.

The rules apply to the MVP shipped in this repo and to every future phase described in [`integration-plan.md`](./integration-plan.md). Phase changes the implementation; phase does not change the rules.

## 1. No automatic sending

- **Why.** A B2B sales reply is a commitment. A misfired auto-send to a VIP account is the kind of mistake that ends careers and contracts. There is no scenario where this tool benefits from the ability to send mail by itself, and many scenarios where that ability would be catastrophic.
- **How.** There is no SMTP code in the build at all. There is no Gmail send adapter. There is no Outlook send adapter. The `--approve` CLI flag does NOT trigger a send; it only writes an approval line into the audit log and unblocks the *display* of cleared drafts. A human still has to copy the draft, paste it into their mail client, read it once more, and press send. That is intentional friction.

## 2. No real credentials

- **Why.** Real credentials in a sample / MVP repo are how leaks happen. This project is a portfolio piece and a teaching tool; it must be impossible to "accidentally" connect it to a real customer mailbox.
- **How.** All adapters in the MVP are mock. The bundled samples use `*.example` and `our-company.example` domains, which are reserved by RFC 2606 and cannot resolve to real mailboxes. There is no token store, no secrets file, no environment-variable contract for OAuth. Phase 2 adds real adapters but as separate modules; the MVP build never gains the ability to authenticate.

## 3. Scoped reads

- **Why.** "Read the inbox" is a privacy footgun. It also encourages prompt-first behaviour: the model browses for context instead of being grounded in known commercial facts. Scoped reads force the agent to look at one customer at a time, the way a human account manager does.
- **How.** `EmailThreadPort` exposes `loadThread(customerId, threadId)` and nothing else. There is no `listInbox`. There is no `searchAcrossCustomers`. The mock adapter loads exactly one thread file per call. Future Gmail or Outlook adapters MUST preserve this shape — they may translate `(customerId, threadId)` into provider-specific identifiers, but they must not expose a list method to the rest of the agent.

## 4. Explicit approval gate

- **Why.** Some decisions are above the agent's pay grade and above the salesperson's pay grade. A refund commitment, a contract concession, an exceptional discount, a cancellation acknowledgement, a legal-language exchange — these all require a human manager to sign off, every time, on every customer, regardless of how confident the model is.
- **How.** `RiskPolicyPort` returns `REQUIRES_MANAGER_APPROVAL` whenever any of the following trigger words / conditions are detected: refund, credit, concession, exceptional discount, cancellation, legal counsel, churn signal on VIP, payment dispute on a contract-active account. When this decision is returned, `AdvisorReportRenderer` prepends the BLOCKED banner and replaces the draft body with `[BLOCKED — manager approval required]`. The proposed wording is still shown so a manager can review it, but it is unmistakably not cleared. `--approve` writes one line into the audit log and unblocks display only — see rule 1.

## 5. Audit every tool-like action

- **Why.** An agent that cannot explain itself is an agent that cannot be trusted with customers. Every external-looking action — every port call — produces evidence. Evidence makes the gate from rule 4 reviewable, makes mistakes diagnosable, and makes the agent legible to compliance.
- **How.** Every adapter is wrapped so that entry, arguments (redacted where appropriate), and outcome are appended to `AuditLogPort`. The CLI prints an `Audit Summary` section at the bottom of every report. The audit format is line-based and grep-able: timestamp, port name, method, key arguments, decision. Audit entries are always in English (see rule 8).

## 6. Privacy in drafts

- **Why.** A draft is intended to leave the building. Anything inside it — even in a draft that is later blocked and only seen by a manager — is one copy-paste away from the customer. Internal-only signals (lifetime value, escalation history, internal pricing floors, account-manager notes, playbook references) MUST NOT appear in either draft.
- **How.** `ReplyDraftPort` operates on a *redacted* view of the customer profile. The full profile is only available to the risk policy and the strategy step; the reply composer cannot see fields tagged internal-only. The MVP enforces this by passing a different record type (`CustomerDraftView`) into `ReplyDraftPort` than into `RiskPolicyPort`. Anything not on `CustomerDraftView` cannot be read at draft time.

## 7. Bulk outbound is out of scope

- **Why.** The product is a copilot for an account manager handling specific customer threads. The moment it generates many outbound messages, it stops being a copilot and starts being a mailing-list tool. Mailing-list tools attract a completely different set of risks (spam, deliverability, anti-bot detection) and a different regulatory surface (CAN-SPAM, GDPR marketing consent). We are not in that business.
- **How.** The CLI accepts exactly one customer profile and exactly one email thread per invocation. There is no batch mode. There is no "for each customer in CRM" loop. Future phases preserve this constraint: even when a CRM MCP server is plugged in, it is used to *enrich* the single-customer flow, not to iterate over customers.

## 8. No detection evasion

- **Why.** The audit trail is the agent's testimony. If the agent paraphrases its own actions, summarises them away, or reorders them so the sequence reads better, the testimony is no longer reliable. Compliance review and incident investigation both depend on the audit trail being literal.
- **How.** Audit lines are written at the moment a port call returns, not at the end of the run. They are written in English. Tool inputs are recorded as the agent supplied them, not as the agent intended them. The CLI does not offer a "compact audit" or "natural-language audit" mode. If a future adapter wants to add structured fields, it appends fields; it does not rewrite existing ones.

---

If you find a path that violates any of these rules — a code path, a configuration, a roadmap item, a sample — file an issue. The rules are the product. They are more important than any feature on the roadmap.
