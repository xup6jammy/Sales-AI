# Sales AI — MCP Server

A Model Context Protocol server that exposes the same customer data the Java engine works on, but as **MCP tools an LLM can call directly**. JSON-RPC 2.0 over stdin/stdout, four whitelisted query tools, no generic SQL.

```
┌──────────────┐  stdio JSON-RPC  ┌─────────────────────┐  JDBC  ┌──────────┐
│ LLM          │ ───────────────▶ │ SalesMcpServer      │ ─────▶ │ SQLite / │
│ (MCP client) │ ◀─────────────── │  4 whitelisted tools│        │ MySQL /  │
└──────────────┘                  └─────────────────────┘        │ Postgres │
                                                                  └──────────┘
```

## What's exposed

| Tool                          | What it does                                                  |
|-------------------------------|---------------------------------------------------------------|
| `customer.findByEmail`        | Look up one customer by primary email (exact, case-insensitive). |
| `customer.findById`           | Look up one customer by id (e.g. `CUST-1042`).                |
| `customer.listOrders`         | Recent orders for one customer, newest first, capped at 50.   |
| `customer.listOpenTickets`    | Open support tickets for one customer.                        |

**What is NOT exposed** — and never will be without a code review:

- Generic `runSql(query)` — the LLM cannot author SQL.
- Anything that scans the whole `customers` table (`searchByName`, `listAll`, `findLike`).
- Any write path. The MCP server is read-only.

This is the safety contract the main `README.md` talks about: prompt injection arriving in customer emails cannot widen the agent's data access, because the data access surface is whitelisted at the server.

## Quick start (SQLite, 90 seconds)

### 1. Get a JDBC driver

```powershell
# PowerShell — see mcp-server/lib/README.md for bash and other DBs
Invoke-WebRequest `
  -Uri 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.1/sqlite-jdbc-3.42.0.1.jar' `
  -OutFile 'mcp-server/lib/sqlite-jdbc-3.42.0.1.jar'
```

Use **3.42.0.1** specifically — newer versions need SLF4J on the classpath.

### 2. Compile the MCP server

```powershell
# PowerShell
$src = Get-ChildItem -Recurse mcp-server/src/main/java -Filter *.java | %{ $_.FullName }
javac -d mcp-server/out -Xlint:all $src
```

```bash
# bash
find mcp-server/src/main/java -name '*.java' | xargs javac -d mcp-server/out -Xlint:all
```

### 3. Seed the demo SQLite database

```bash
java -cp "mcp-server/lib/sqlite-jdbc-3.42.0.1.jar:mcp-server/out" \
     com.example.salesai.mcp.SeedData
```

PowerShell uses `;` instead of `:` between classpath entries. Same on Windows otherwise.

This applies `mcp-server/schema/sqlite.sql` and inserts `mcp-server/samples/seed.sql` (the same Lumora Robotics scenario the engine ships with) into `mcp-server/demo.db`.

### 4. Wire into Claude Code

Add an entry to your `.claude/mcp-config.json` (or the equivalent for your platform):

```json
{
  "mcpServers": {
    "sales-advisor": {
      "command": "java",
      "args": [
        "-cp",
        "mcp-server/lib/sqlite-jdbc-3.42.0.1.jar;mcp-server/out",
        "com.example.salesai.mcp.SalesMcpServer",
        "--db", "jdbc:sqlite:mcp-server/demo.db"
      ]
    }
  }
}
```

Use `:` instead of `;` on macOS/Linux. The `cwd` for the spawned process should be the repository root.

Restart Claude Code. The four tools above appear under the `sales-advisor` MCP server.

## Verifying without Claude Code

You can drive the server by hand to sanity-check it. Pipe newline-delimited JSON-RPC requests on stdin:

```bash
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{}}}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"customer.findByEmail","arguments":{"email":"wm.chen@lumora-robotics.example"}}}' \
  | java -cp "mcp-server/lib/sqlite-jdbc-3.42.0.1.jar:mcp-server/out" \
         com.example.salesai.mcp.SalesMcpServer \
         --db jdbc:sqlite:mcp-server/demo.db
```

Each line gets one JSON-RPC reply on stdout. Diagnostics go to stderr so they never collide with the protocol.

> **Windows note.** PowerShell's pipe (`|`) prepends a UTF-8 BOM to the first line, which the server will reject as "Parse error" while still answering everything that follows. To avoid the BOM, write the requests to a file with `Set-Content -Encoding ASCII` or `-Encoding utf8NoBOM` and pipe via `Get-Content -Raw`.

## Using a real database (MySQL / Postgres)

1. Drop the appropriate driver jar in `mcp-server/lib/` (see `lib/README.md`).
2. Apply the schema in `mcp-server/schema/{mysql,postgres}.sql` against your database.
3. Run `SeedData` — or load your own data — pointing at the right JDBC URL:

```bash
java -cp "mcp-server/lib/mysql-connector-j-8.4.0.jar:mcp-server/out" \
     com.example.salesai.mcp.SeedData \
     --db jdbc:mysql://localhost:3306/sales \
     --schema mcp-server/schema/mysql.sql \
     --user sales_app --password '...'
```

4. Update the Claude Code MCP config to point `--db` at the same JDBC URL.

The MCP server detects the dialect from the URL prefix (`jdbc:sqlite:` / `jdbc:mysql:` / `jdbc:postgresql:`) and uses `?`-bound parameters everywhere, so the same tool code works across all three.

## Layout

```
mcp-server/
├── README.md                   ← you are here
├── lib/                        ← JDBC driver jars (gitignored)
│   └── README.md               ← which jar to download where from
├── out/                        ← compiled .class files (gitignored)
├── schema/
│   ├── sqlite.sql              ← demo / dev
│   ├── mysql.sql
│   └── postgres.sql
├── samples/
│   └── seed.sql                ← mirrors samples/customer-profile.json
└── src/main/java/com/example/salesai/mcp/
    ├── SalesMcpServer.java     ← entry point: stdio loop + dispatch
    ├── SeedData.java           ← one-shot DB seeding utility
    ├── protocol/
    │   ├── MiniJson.java       ← parser + serializer (zero deps)
    │   ├── JsonRpc.java        ← JSON-RPC 2.0 envelope helpers
    │   └── StdioTransport.java ← newline-delimited stdin/stdout loop
    ├── db/
    │   ├── Database.java       ← single JDBC connection holder
    │   └── QueryRunner.java    ← prepared-statement helper
    └── tools/
        ├── Tool.java           ← interface every tool implements
        ├── ToolRegistry.java   ← list / call dispatcher
        ├── FindCustomerByEmail.java
        ├── FindCustomerById.java
        ├── ListOrders.java
        └── ListOpenTickets.java
```

## Adding a new tool

The whitelist is the safety boundary, so new tools are deliberately a code change:

1. Implement `tools/Tool.java`. Keep the SQL prepared, parameters bound, and the input schema tight.
2. Register the tool in `SalesMcpServer.main` in the `List.of(...)` literal.
3. Add a row to the table at the top of this README.
4. Recompile.

Resist the urge to add a generic `runSql(query)` tool. The whole point of this design is that the LLM cannot author SQL — that's how we honour the "scoped reads" promise in `skills/sales-ai/SKILL.md`.

## Troubleshooting

- **`No suitable driver found for jdbc:sqlite:...`** — the driver jar is missing or the wrong version. Use SQLite-JDBC 3.42.0.1 (3.43+ needs SLF4J).
- **`NoClassDefFoundError: org/slf4j/LoggerFactory`** — you have SQLite-JDBC ≥ 3.43. Either downgrade to 3.42.0.1 or also drop `slf4j-api` and `slf4j-nop` jars in `lib/`.
- **`SQLITE_ERROR ... near "out": syntax error` from `SeedData`** — make sure the schema/seed paths point at the actual `.sql` files, not a directory.
- **Claude Code says "MCP server failed to start"** — run the `java -cp ... SalesMcpServer --db ...` command in a terminal first; the stderr diagnostics will tell you why.
