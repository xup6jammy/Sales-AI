# Customer Email Sales Advisor

*An AI sales copilot that reads your customer's email like a senior account manager would — context first, draft last, never hits send.*

![Java 21](https://img.shields.io/badge/Java-21-007396)
![License: MIT](https://img.shields.io/badge/License-MIT-green)
![Status: MVP](https://img.shields.io/badge/Status-MVP-blue)
![No dependencies](https://img.shields.io/badge/Dependencies-None-lightgrey)

---

## TL;DR

- A Java 21 copilot for B2B account managers. It loads the customer's profile and commercial history first, reads only the relevant thread, classifies intent and tone, evaluates risk against an explicit policy, and produces two reply drafts plus follow-up actions.
- It is not a chatbot. It is context-grounded by design. Refunds, legal language, contract concessions, exceptional discounts, cancellation talk, and churn signals on VIP accounts force a hard manager-approval gate that blocks the drafts.
- Run it in 60 seconds with the stock JDK, no dependencies, no credentials, no network — see [Run it](#run-it).

## The architectural premise

> **The Skill is the agent. The Java MVP is the engine, replaceable per phase.**

```
Claude Code  ->  reads  ->  skills/customer-email-sales-advisor/SKILL.md
                                       |
                                       | orchestrates
                                       v
                       Tool layer (replaceable per phase)
                                       |
        +------------------------------+------------------------------+
        v                              v                              v
   MVP today:                   Phase 2:                       Phase 3:
   Java CLI                     + Gmail / Outlook MCP          + CRM MCP, Text2SQL,
   (this repo)                  + Agents-Flex Skills           + RAG knowledge base
```

The Skill in `skills/customer-email-sales-advisor/SKILL.md` tells Claude what to do, in what order, with what guardrails. Today the only tool it can call is the Java CLI in this repo. Tomorrow it calls a Gmail MCP server, an Outlook MCP server, a CRM MCP server, and an LLM-backed reply composer wrapped as an Agents-Flex Skill. The workflow does not change. The safety rules do not change. Only the implementations behind the ports move.

This is what makes the project useful as a study piece, not just as a demo: the engine you read today is exactly the engine that an MCP-backed production deployment will eventually replace, port by port.

## What it does (workflow)

The CLI runs eleven steps in this order, every time:

1. Identify the customer (by id or by email, from CLI args).
2. Load the customer's commercial profile: tier, contract status, payment status, recent orders, open tickets, account-manager notes.
3. Load the relevant email thread for that customer. One thread, never the inbox.
4. Summarise the thread factually: who said what, when, what is being asked, what has already been promised.
5. Classify business intent into one of `INQUIRY`, `QUOTATION`, `COMPLAINT`, `RENEWAL`, `PAYMENT_ISSUE`, `DELIVERY_DELAY`, `TECHNICAL_SUPPORT`, `NEGOTIATION`, `CHURN_RISK`, `UNKNOWN`.
6. Classify emotional tone: neutral, frustrated, escalating, conciliatory, urgent, or formal.
7. Evaluate risk against the explicit policy. Refund / legal / contract / exceptional discount / cancellation / churn-on-VIP all force `REQUIRES_MANAGER_APPROVAL`.
8. Decide a reply strategy in one or two sentences: acknowledge, commit, defer, escalate, ask for time.
9. Generate two reply drafts: Option A is safe and formal, Option B is warm and relationship-focused. Both respect the customer's preferred language.
10. Surface the approval gate. If the risk decision blocks the drafts, the report shows them as `[BLOCKED — manager approval required]` with the proposed wording quoted for review.
11. Render the report and an audit summary listing every port call. Optionally record the interaction back to CRM.

## Sample output

The bundled demo ships a representative case: Wei-Ming Chen, the procurement lead at Lumora Robotics Co., Ltd. (a VIP customer with overdue payment), is asking for a partial refund and credit on a delayed order, with the August renewal now in question.

The block below is the **actual stdout** of `java -cp out com.example.salesadvisor.SalesAdvisorCli` against the bundled samples — not a screenshot, not a hand-edited mock. Everything you see is produced by the deterministic workflow in `app/AdvisorWorkflow.java` and rendered by `app/AdvisorReportRenderer.java`. The full transcript also lives at [`samples/advisor-output.md`](samples/advisor-output.md).

```
=== Customer Email Sales Advisor — Report ===
!! DRAFTS ARE BLOCKED — manager approval required before this reply can leave the building !!

Customer Context
- Name: Wei-Ming Chen
- Company: Lumora Robotics Co., Ltd.
- Tier: VIP
- Contract status: ACTIVE (renews 2026-08-31)
- Payment status: OVERDUE_30D
- Recent orders:
    * SO-2026-0188 — 2026-04-12 — $42000 — DELIVERED (On-time delivery, signed acceptance)
    * SO-2026-0231 — 2026-04-29 — $18500 — DELAYED (Logistics partner missed ETA by 9 days)
- Recent support state:
    * SUP-7781 [HIGH] since 2026-04-25: Vision module misalignment after firmware 4.2 rollout

Email Summary
- Subject: Order SO-2026-0231 delay + firmware issue — refund expected
- Current intent: TECHNICAL_SUPPORT
- Emotional tone: URGENT
- Key customer ask: Kelly, four days, no plan. Our CTO is now in the loop and is asking about the renewal in August. Please confirm by tomorrow: (1) revised delivery date, (2) refund or credit amount, (3) firmware fix ETA. Otherwise we will pause the renewal d...

Risk Assessment
- Risk level: REQUIRES_MANAGER_APPROVAL
- Reasons:
    * Customer signalled churn risk (mentioned alternative vendors / pause renewal / cancel).
    * Customer asked for refund or credit.
    * VIP customer has an overdue payment (OVERDUE_30D).
    * Customer has an open HIGH-priority support ticket.
- Requires manager approval: YES

Recommended Reply Strategy
- Tone: formal, careful, no commitments (the customer is signalling urgency — acknowledge time pressure explicitly)
- Position: acknowledge, no commitments yet, escalate
- Avoid saying:
    * Promising any contractual concession without manager approval
    * Confirming a refund or credit amount in the reply
- Allowed commitments:
    * Acknowledge receipt and the urgency of the situation today
    * Pull together logistics, engineering, and account management within 24 hours
    * Provide a written status update with concrete dates by end of next business day
- Next best action: Hand off to Kelly Wu with full context; do not reply until approved

Draft Option A: Safe / Formal
Subject: Re: Order SO-2026-0231 delay + firmware issue — refund expected — recovery plan
Body:
Dear Wei-Ming Chen,

Thank you for the directness of your message. I take the points you raised seriously, and I want to address them in order.
I understand the impact this has had on your operations and on your team's confidence in us.

Here is what I can confirm today:
  - Acknowledge receipt and the urgency of the situation today
  - Pull together logistics, engineering, and account management within 24 hours
  - Provide a written status update with concrete dates by end of next business day

Because some of the items you raised — in particular any commercial concession — fall outside what I can confirm in writing today, I am bringing them to Kelly Wu's attention so we can come back to you with a single, signed-off response.

Please consider this message a status update rather than a final commercial response.

Next step from our side: Hand off to Kelly Wu with full context; do not reply until approved

Best regards,
Kelly Wu

Draft Option B: Warm / Relationship-Focused
Subject: Re: Order SO-2026-0231 delay + firmware issue — refund expected — recovery plan
Body:
Hi Wei-Ming Chen,

Thanks for being so direct with me — I'd much rather hear it this way than find out later. Let me address each point.
I know this hasn't been the experience you expected from us, and I'm not going to pretend otherwise.

Here is what I can lock in for you right now:
  - Acknowledge receipt and the urgency of the situation today
  - Pull together logistics, engineering, and account management within 24 hours
  - Provide a written status update with concrete dates by end of next business day

On the commercial side (anything that looks like a refund, credit, or change to the contract), I want to be honest: I won't commit to a number in this email until Kelly Wu has signed it off — that's how we keep our promises clean.

Treat this as me keeping you in the loop, not as the final word on the commercial side.

What I'm doing next: Hand off to Kelly Wu with full context; do not reply until approved

Talk soon,
Kelly Wu

Follow-Up Actions
- Brief the manager — owner: Kelly Wu, due: today
    Walk the manager through the inbound message, the risk reasons, and the proposed reply before any draft leaves the building.
- Engineering update on open ticket — owner: Engineering lead, due: within 48 hours
    Confirm a firmware fix ETA for the open HIGH-priority ticket and write it up in customer-friendly language.
- Schedule executive check-in — owner: Kelly Wu, due: this week
    VIP customer — set up a short call with our account exec to keep the relationship anchored.

Audit Summary
- [2026-05-06T08:55:16Z] LOOKUP_CUSTOMER: email=wm.chen@lumora-robotics.example
- [2026-05-06T08:55:16Z] LOAD_THREAD: customerEmail=wm.chen@lumora-robotics.example
- [2026-05-06T08:55:16Z] CLASSIFY_INTENT: thread=THR-90188
- [2026-05-06T08:55:16Z] INTENT_CLASSIFIED: TECHNICAL_SUPPORT
- [2026-05-06T08:55:16Z] CLASSIFY_TONE: thread=THR-90188
- [2026-05-06T08:55:16Z] TONE_CLASSIFIED: URGENT
- [2026-05-06T08:55:16Z] EVALUATE_RISK: intent=TECHNICAL_SUPPORT tone=URGENT
- [2026-05-06T08:55:16Z] RISK_LEVEL: REQUIRES_MANAGER_APPROVAL requiresManagerApproval=true
- [2026-05-06T08:55:16Z] DECIDE_STRATEGY: level=REQUIRES_MANAGER_APPROVAL
- [2026-05-06T08:55:16Z] GENERATE_DRAFTS: tone=formal, careful, no commitments (the customer is signalling urgency — acknowledge time pressure explicitly)
- [2026-05-06T08:55:16Z] RECOMMEND_FOLLOWUPS: intent=TECHNICAL_SUPPORT
- [2026-05-06T08:55:16Z] EVALUATE_APPROVAL: requiresManagerApproval=true
- [2026-05-06T08:55:16Z] APPROVAL_DENIED: manager flag=false; reasons=[Customer signalled churn risk (mentioned alternative vendors / pause renewal / cancel)., Customer asked for refund or credit., VIP customer has an overdue payment (OVERDUE_30D)., Customer has an open HIGH-priority support ticket.]
- [2026-05-06T08:55:16Z] CRM_RECORD: customerId=CUST-1042 summary=Advisor reviewed thread THR-90188; intent=TECHNICAL_SUPPORT; risk=REQUIRES_MANAGER_APPROVAL; drafts BLOCKED

=== End of Report ===
```

Re-running with `--approve` does NOT send mail. It writes one extra audit line recording the approval (`APPROVAL_GRANTED`), drops the BLOCKED banner, and changes the closing CRM record to `drafts READY`. A human still has to copy the wording, paste it into the mail client, read it once more, and press send. That is intentional friction. See [`docs/safety-rules.md`](docs/safety-rules.md).

> **Drafts are in English** in the MVP because the template adapter is deterministic. Generating drafts in the customer's preferred language (here `zh-TW`) is part of Phase 4 — see [`docs/integration-plan.md`](docs/integration-plan.md) — when `TemplateReplyDraftAdapter` is replaced by an LLM-backed Agents-Flex Skill. The strategy and risk decisions stay language-agnostic so they remain auditable.

## Run it

You need a stock JDK 21 and nothing else. No build tool. No network. No credentials.

**PowerShell (Windows):**

```powershell
javac -d out (Get-ChildItem -Recurse src/main/java/*.java | %{$_.FullName})
java -Dstdout.encoding=UTF-8 -cp out com.example.salesadvisor.SalesAdvisorCli
```

> On Windows, `-Dstdout.encoding=UTF-8` makes em-dashes and 中文 render correctly even when the console code page is not 65001. Drop the flag if you have already run `chcp 65001`.

**bash (macOS / Linux / WSL / Git Bash):**

```bash
find src/main/java -name '*.java' | xargs javac -d out
java -cp out com.example.salesadvisor.SalesAdvisorCli
```

The CLI takes a small set of flags. All are optional; the defaults point at the bundled samples.

| Flag | Meaning |
|------|---------|
| `--customer-profile <path>` | Path to the customer profile JSON. Defaults to `samples/customer-profile.json`. |
| `--email-thread <path>` | Path to the email thread JSON. Defaults to `samples/email-thread.json`. |
| `--approve` | Marks the report as manager-approved. Writes an extra audit line; unblocks display of drafts. Does NOT send mail. |

Examples:

```bash
java -cp out com.example.salesadvisor.SalesAdvisorCli \
  --customer-profile samples/customer-profile.json \
  --email-thread samples/email-thread.json
```

```powershell
java -cp out com.example.salesadvisor.SalesAdvisorCli `
  --customer-profile samples/customer-profile.json `
  --email-thread samples/email-thread.json `
  --approve
```

## Why this is not a chatbot

This is the line that defines the project, so it is worth being explicit about it.

A chatbot is prompt-first: the user types something, the model reads it, the model replies. Context is whatever scrolls back in the conversation, possibly augmented with retrieved snippets. The model's job is to respond to the message in front of it.

A sales copilot is context-first. Before the model sees the customer's email at all, the agent loads the customer's tier, their contract status, their payment status, their recent orders, their open support tickets, and the account manager's notes. The email is read against that backdrop. The risk evaluation is performed against that backdrop. The drafts are composed against a redacted projection of that backdrop. The order matters: a chatbot reads the email and then asks "do I happen to know who this is?". A copilot answers "who this is" before it reads anything.

There is also a hard safety boundary. A refund request, a legal mention, a contract concession, an exceptional discount, a cancellation, or a churn signal on a VIP account forces the manager-approval gate. The drafts are produced, but they are blocked. The audit trail explains exactly why. A chatbot has no such gate; this agent has one as its first-class output.

The audit trail itself is the third thing chatbots typically do not have. Every port call writes one line. The CLI prints the audit summary at the bottom of every report. If a decision in the report looks wrong, you can read backwards from the conclusion through the steps that produced it. That is what makes this useful for B2B work: the model is legible.

## Architecture in one screen

The full architecture lives in [`docs/architecture.md`](docs/architecture.md). The short version: hexagonal layout, no DI framework, Java 21 records for the domain, a hand-rolled JSON reader instead of Jackson or Gson, and a clean port → MCP mapping that drives the entire roadmap.

| Port | Future replacement |
|------|--------------------|
| `CustomerContextPort` | CRM MCP server, Text2SQL on customer DB |
| `EmailThreadPort` | Gmail MCP / Outlook MCP / IMAP MCP |
| `RiskPolicyPort` | Policy engine, eventually LLM with structured output |
| `ReplyDraftPort` | LLM via Agents-Flex Skill |
| `CrmPort` | CRM MCP write operations |
| `ApprovalPort` | Slack approval bot, ticketing system |
| `AuditLogPort` | OpenTelemetry, Splunk, internal audit DB |

Each phase of [`docs/integration-plan.md`](docs/integration-plan.md) replaces one or two of these ports. The other packages do not move.

## Roadmap

- [x] **Phase 1 — MVP.** Mock adapters, deterministic classifiers, console audit. This repo.
- [ ] **Phase 2 — Real email.** Replace `MockEmailThreadAdapter` with Gmail MCP / Outlook MCP. Reads remain customer-scoped.
- [ ] **Phase 3 — Real CRM.** Replace `MockCustomerContextAdapter` with CRM MCP and Text2SQL on a customer DB.
- [ ] **Phase 4 — Real LLM drafts.** Replace `TemplateReplyDraftAdapter` with an Agents-Flex Skill calling Claude / Bedrock / a local LLM, with prompt caching on the stable preamble.
- [ ] **Phase 5 — Approval routing.** Replace `ManualApprovalAdapter` with a Slack approval bot or a ticketing-system integration.
- [ ] **Phase 6 — Knowledge base / RAG.** Plug a vector store of past won / lost playbooks behind a new port.
- [ ] **Phase 7 — Spring Boot service.** Wrap the workflow in Spring Boot, expose it as an MCP server other Claude Code skills can call.

The detailed shape of each phase, the new safety considerations it introduces, and the OAuth scopes we deliberately do NOT request are in [`docs/integration-plan.md`](docs/integration-plan.md).

## Borrowed patterns, not borrowed code

The project owes a clear conceptual debt to several open-source efforts. None of their source code lives in this repo.

- [Agents-Flex](https://github.com/agents-flex/agents-flex) — Java agent framework. We adopted the Skill-as-spec philosophy and a port / adapter shape that mirrors how an Agents-Flex Skill will plug in at Phase 4. We did not copy any source file, package layout, or class name.
- [marlinjai/email-mcp](https://github.com/marlinjai/email-mcp) — unified email MCP across providers. We adopted the single-port-across-providers shape for `EmailThreadPort`. We do not ship an MCP server; we plan to consume one.
- Public Gmail MCP server examples — read thread, read message, create draft, send-after-approval. We adopted thread-scoped reads, drafts-before-sends, and the approval-before-write posture.
- Public CRM MCP server examples — get customer, recordInteraction-style writes. We adopted the read-mostly write surface and the structured-payload write shape.

We did not vendor, fork, or copy source code from any of these projects. The full per-project breakdown of what was adopted and what was explicitly not is in [`docs/borrowed-patterns.md`](docs/borrowed-patterns.md).

## Skill usage in Claude Code

The agent definition lives in [`skills/customer-email-sales-advisor/SKILL.md`](skills/customer-email-sales-advisor/SKILL.md). Drop the `skills/customer-email-sales-advisor/` folder into your Claude Code skills directory (or use the project-local one), then ask Claude things like:

- "幫我看一下王經理那封信怎麼回。"
- "Take a look at the Lumora thread — Wei-Ming is asking for a refund."
- "Customer CUST-1042 just escalated. Walk me through it."

Claude will follow the eleven-step workflow in the Skill, call the bundled Java CLI as the tool layer, and surface the report. When the risk decision is `REQUIRES_MANAGER_APPROVAL`, Claude will tell you the drafts are blocked and stop until you grant approval explicitly.

## 繁體中文簡介

### 這是什麼？

`Customer Email Sales Advisor` 是一個用 Java 21 寫的 B2B 業務 / 客戶經理副駕駛工具。它不是聊天機器人，而是一個會先把客戶背景搞清楚、再去看信、再決定怎麼回的工具。它會載入客戶的合約狀態、付款狀態、最近訂單、開立中的工單、以及 AM 留下的內部備忘，再去讀那一條 thread，然後依照公司明文政策評估風險，最後產出兩封草稿（一封正式、一封拉近關係的）和後續行動建議。

### 為什麼不是聊天機器人？

聊天機器人是「先讀訊息再想要不要查資料」；這個工具是「先把客戶資料抓齊再來讀信」。順序差很多。更重要的是，凡是退費、法務、合約讓步、特例折扣、解約、或是 VIP 客戶出現流失訊號，系統會強制觸發 `REQUIRES_MANAGER_APPROVAL`，把草稿擋下來，並且把每一個工具呼叫寫進稽核紀錄。沒有任何一條路徑可以「直接寄信出去」——`--approve` 也不會幫你按送出，它只是把核可這件事寫進 audit log，然後把草稿從擋下狀態解除顯示而已。最後一步永遠是人類複製、貼上、再讀一次、自己按送出。

### 執行方式

```powershell
javac -d out (Get-ChildItem -Recurse src/main/java/*.java | %{$_.FullName})
java -cp out com.example.salesadvisor.SalesAdvisorCli
```

預設會跑內附的範例：Lumora Robotics 的陳經理因為訂單延誤與韌體問題要求退費，並暗示 8 月不續約。你會直接看到 `REQUIRES_MANAGER_APPROVAL` 觸發、兩封 zh-TW 草稿被擋下、以及完整的稽核摘要。

### 未來路線

短期目標是把 mock adapter 換成 Gmail / Outlook MCP（Phase 2）和 CRM MCP（Phase 3），中期把樣板回信換成 Agents-Flex Skill 呼叫 LLM（Phase 4），加上 Slack 核可機器人（Phase 5）與過往戰役的 RAG（Phase 6），最後用 Spring Boot 包成 MCP 伺服器，讓其他 Claude Code skill 直接把它當成一個 tool 呼叫（Phase 7）。每一個階段只動一兩個 port，工作流程與安全規則維持不變。

## Contributing

Issues, suggestions, and counter-examples are welcome — particularly counter-examples. If you can find a request that this agent handles badly (a thread it misclassifies, an approval gate it should have triggered but did not, a draft that leaks an internal-only field), please open an issue with the input that produced the bad output. The safety rules in [`docs/safety-rules.md`](docs/safety-rules.md) are the product, and protecting them is the most useful contribution.

## License

MIT. See [`LICENSE`](LICENSE).

## Suggested GitHub topics

`java`, `java21`, `ai-agent`, `email-copilot`, `sales-automation`, `mcp`, `agents-flex`, `claude-code`, `hexagonal-architecture`, `llm-tools`
