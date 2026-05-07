# Outlook Integration

## What this enables

Passing `--email-mcp outlook` tells the Sales-AI engine to fetch real Outlook/Microsoft 365 email threads via an MCP server instead of reading mock JSON files. The engine calls the Outlook MCP server's tool at runtime, parses the response through `McpEmailThreadAdapter`, and feeds the result into the scoring/analysis pipeline.

## Ecosystem maturity note

The Outlook MCP server ecosystem is significantly less mature than the Gmail equivalent. This guide documents `mcp-server-outlook` (invoked via `uvx`) as a working placeholder. If you find a different package better suits your environment, substitute it in `mcp-config.json` and update the auth steps accordingly. The schema gap described below applies regardless of which Outlook MCP package you use.

## Prerequisites

- `uvx` available on your PATH — install via [astral.sh/uv](https://astral.sh/uv) (`pip install uv` or the standalone installer)
- A Microsoft 365 or Outlook.com account
- The Sales-AI CLI built (`ant compile` / `javac`)

## One-time OAuth setup

The `mcp-server-outlook` package follows the Microsoft Graph OAuth 2.0 device-code or browser-redirect flow. The exact invocation depends on the package version; the general pattern is:

```sh
uvx mcp-server-outlook auth
```

If that subcommand is not available in your installed version, the package typically starts a local HTTP server and prints a Microsoft login URL to stdout. Open the URL in a browser, sign in with your Microsoft account, and grant the requested Mail.Read (or equivalent) scope. The token is cached locally (location printed during auth).

> Check the package's own documentation for the authoritative auth command — Microsoft Graph OAuth flows vary by app registration and package implementation.

## Wiring it up

1. Copy the example config to the active config file:

   ```sh
   cp mcp-config.json.example mcp-config.json
   ```

2. Run the CLI with the `--email-mcp` flag:

   ```sh
   java -cp out com.example.salesai.SalesAiCli \
     --email-mcp outlook \
     --email customer@example.com
   ```

   The `--mcp-config` flag defaults to `mcp-config.json` in the working directory. Pass `--mcp-config /path/to/file.json` to override.

## Important notes

### Schema gap (current limitation)

The Outlook MCP server's reply shape does not exactly match what `McpEmailThreadAdapter` expects. The adapter looks for:

```json
{
  "thread_id": "...",
  "subject": "...",
  "messages": [
    {
      "message_id": "...",
      "from": "...",
      "to": ["..."],
      "sent_at": "...",
      "direction": "inbound|outbound",
      "body": "..."
    }
  ]
}
```

Raw Outlook MCP responses wrap messages in Microsoft Graph conventions (e.g., `conversationId`, `internetMessageId`, `receivedDateTime`, `bodyPreview`). Until a normalising wrapper MCP is added (Phase 5+ roadmap), **the adapter returns `Optional.empty()` for raw Outlook responses**. You will see the following in stderr:

```
[mcp-email] could not parse MCP reply
```

The engine continues without email context in this case. Real production use requires a thin wrapper MCP that transforms the Microsoft Graph shape into the schema above.

### Privacy

Granting OAuth gives this engine read access to your Outlook mailbox. The engine does NOT send message content to any LLM unless you also pass `--llm` (Phase 4). When `--llm` is set, message bodies are included in the prompt. See the relevant LLM provider integration doc (e.g., `docs/integrations/anthropic.md`) for data-handling details.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `MCP server failed to start` | `uvx` not on PATH or package install failed | Run `uvx mcp-server-outlook` standalone to verify; install `uv` if missing |
| `tool call failed: 401` | OAuth token expired or revoked | Re-run the auth flow for your package |
| `tool call failed: 403` | Insufficient Microsoft Graph scope | Re-authorise and ensure Mail.Read scope is granted |
| `[mcp-email] could not parse MCP reply` | Known schema gap | See schema gap note above; a normalising wrapper is needed for production use |
| `No MCP servers configured` | `mcp-config.json` not found | Copy `mcp-config.json.example` to `mcp-config.json` in your working directory |

## Package reference

- `mcp-server-outlook` via `uvx` (placeholder — substitute your preferred Outlook MCP package)
- Microsoft Graph Mail API docs: https://learn.microsoft.com/en-us/graph/api/resources/mail-api-overview
- `uv` installer: https://astral.sh/uv
