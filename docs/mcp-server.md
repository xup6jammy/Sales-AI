# MCP Server: design notes

This document captures the **why** behind the MCP server's design choices. Operational instructions live in [`mcp-server/README.md`](../mcp-server/README.md).

## What problem does this solve

The Java engine in `src/` reads its data from JSON files via `MockCustomerContextAdapter`. That's enough to demonstrate the workflow, but it has two ceilings:

1. **The data is fake.** "It works on a JSON file" is the criticism every reviewer of an agent project lobs at the README. Until the agent reads from a real database, "it really queries customer state" is a claim, not a fact.
2. **Only the engine can use it.** A Claude Code skill running in a separate process can't reach the JSON the engine loaded — they don't share memory. If we want Claude Code to make decisions based on the same customer state the engine sees, we need a protocol both can speak.

The MCP server fixes both at once. It is a long-running JDBC client that exposes the customer data as MCP tools. Claude Code spawns it; the engine can also point at the same SQLite file via `--db`. Same data, two consumers.

## Core decision: whitelist tools, not raw SQL

The MCP server exposes four named tools, each backed by one prepared SQL statement. The LLM can supply parameter values; it cannot author SQL fragments.

We considered — and rejected — exposing a generic `runSql(query)` tool. Rationale:

- A generic SQL tool moves the safety boundary from the server (clear, code-reviewed) to the prompt (fuzzy, LLM-mediated). Prompt injection in inbound customer email becomes a SQL injection vector.
- `SKILL.md` makes a hard promise about "scoped reads" — never the inbox, never another customer's mail. A `runSql` tool makes that promise unenforceable.
- The four tools cover every read the engine's `CustomerContextPort` performs today. Future tools are a code change, which is exactly the gating we want.

This is the same instinct behind making `RiskRules.java` deterministic Java instead of an LLM call. **Safety-critical paths are written in code, where they can be reviewed and tested. Non-critical decisions go to the LLM.**

## Why JSON-RPC over stdio

MCP supports several transports; stdio is the one Claude Code uses for spawned servers. The transport is newline-delimited JSON-RPC 2.0:

- Each request is one JSON object on one line.
- Each response goes on stdout, one JSON object per line.
- Server diagnostics go to stderr, never to stdout, so they don't collide with the protocol.
- Notifications (no `id` field) get no response — the spec is firm on this.

The whole transport is about 80 lines of Java in [`StdioTransport.java`](../mcp-server/src/main/java/com/example/salesai/mcp/protocol/StdioTransport.java). No external MCP SDK is needed for a server this size; the protocol is simple enough that a hand-rolled implementation fits the "zero dependencies for the engine, one driver jar for the MCP server" theme.

## Why one connection per server, not a pool

The MCP server processes requests serially over stdio. Concurrent requests aren't possible at this transport layer — there's no demultiplexing of in-flight messages. A connection pool would be over-engineering.

If a future transport (HTTP, websocket) introduces concurrency, the swap is local to [`Database.java`](../mcp-server/src/main/java/com/example/salesai/mcp/db/Database.java); the tool implementations don't care.

## Why SQLite for the demo

SQLite is the only mainstream RDBMS that needs zero infrastructure: no server process, no port, no init script, no Docker. The driver is one jar. The database is one file. A reviewer can clone the repo, download one jar, run the seed command, and watch the MCP server answer real SQL queries — with no shell setup.

MySQL and Postgres are first-class targets; the schema files are committed and tested. The choice is "default to the lowest-friction option, support the realistic ones."

## Why dialect detection by JDBC URL prefix

Each tool's SQL is written in portable ANSI: `?` placeholders, `LOWER()`, no vendor-specific functions. So one set of queries works on all three databases. The only place we look at the dialect is in [`Database.detectDialect`](../mcp-server/src/main/java/com/example/salesai/mcp/db/Database.java), and that's currently informational only (printed to stderr at startup).

If a tool ever needs vendor-specific SQL — say, `RETURNING` clauses on Postgres — `dialect()` is the seam where that branch goes. We resisted putting it there pre-emptively (YAGNI).

## Why the engine has its own JDBC adapter

[`JdbcCustomerContextAdapter`](../src/main/java/com/example/salesai/adapters/JdbcCustomerContextAdapter.java) duplicates a few of the same SELECTs that live in the MCP server. We could have made the engine call its own MCP server over stdio to get customer data, but that's strictly worse:

- Two extra hops (Java → stdio → JDBC vs. Java → JDBC).
- A failure mode where one half boots and the other doesn't.
- A protocol layer between two pieces of code that already share types.

The schema is the contract, not the MCP protocol. Both consumers point at the same database via JDBC. The MCP server only earns its keep when the consumer is in a different process — which is the case for Claude Code.

## What "ports" still mean after this

The hexagonal-architecture story in [`docs/architecture.md`](architecture.md) doesn't change. `CustomerContextPort` still has two implementations:

- `MockCustomerContextAdapter` — JSON file, zero infrastructure.
- `JdbcCustomerContextAdapter` — relational database.

A future `McpCustomerContextAdapter` (engine talking *to* an MCP server) could be added if we ever need it, but today that adapter has no caller.

## What's deliberately not in the MCP server

- **Writes.** No `recordInteraction`, no `updateNote`, no `markEmailSent`. Writes belong on `CrmPort`, which lives on the engine side and stays there.
- **Email reads.** Gmail / Outlook MCP servers already exist and are mature. We use them, we don't replace them.
- **LLM calls.** Drafting is `ReplyDraftPort`'s job. The MCP server does not call out to Claude / Bedrock / etc.
- **Auth flows.** SQLite needs no auth; MySQL/Postgres credentials are passed as command-line flags. OAuth for Gmail-style flows belongs in a separate transport-specific server.

These omissions keep the surface small, which keeps the safety review tractable.
