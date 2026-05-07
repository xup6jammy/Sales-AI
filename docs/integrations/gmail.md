# Gmail Integration

## What this enables

Passing `--email-mcp gmail` tells the Sales-AI engine to fetch real Gmail threads via an MCP server instead of reading mock JSON files. The engine calls the Gmail MCP server's tool at runtime, parses the response through `McpEmailThreadAdapter`, and feeds the result into the scoring/analysis pipeline.

## Prerequisites

- Node.js 20 or later (required by the MCP server package)
- `npx` available on your PATH
- A Google account with Gmail
- The Sales-AI CLI built (`ant compile` / `javac`)

## One-time OAuth setup

Run the auth command once to open a browser and grant Gmail read-only access:

```sh
npx @gongrzhe/server-gmail-autoauth-mcp auth
```

A browser window opens. Sign in to your Google account and grant the requested scope (Gmail read-only). After approval, the token is cached locally (typically at `~/.gmail-mcp/credentials.json` — the exact path is printed to stdout by the auth command). You do not need to repeat this step unless the token expires or you revoke access.

> If the above `auth` subcommand does not work with the version you install, check the package README at https://www.npmjs.com/package/@gongrzhe/server-gmail-autoauth-mcp for the current invocation — the auth flow details can change between package releases.

## Wiring it up

1. Copy the example config to the active config file:

   ```sh
   cp mcp-config.json.example mcp-config.json
   ```

2. Run the CLI with the `--email-mcp` flag:

   ```sh
   java -cp out com.example.salesai.SalesAiCli \
     --email-mcp gmail \
     --email customer@example.com
   ```

   The `--mcp-config` flag defaults to `mcp-config.json` in the working directory. Pass `--mcp-config /path/to/file.json` to override.

## Important notes

### Schema gap (current limitation)

The Gmail MCP server's reply shape does not exactly match what `McpEmailThreadAdapter` expects. The adapter looks for:

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

Raw Gmail MCP responses use a different structure. Until a normalising wrapper MCP is added (Phase 5+ roadmap), **the adapter returns `Optional.empty()` for raw Gmail responses**. You will see the following in stderr:

```
[mcp-email] could not parse MCP reply
```

The engine continues without email context in this case. Real production use currently requires a thin wrapper MCP that transforms Gmail's output into the schema above.

### Privacy

Granting OAuth gives this engine read access to your Gmail. The engine does NOT send Gmail message content to any LLM unless you also pass `--llm` (Phase 4). When `--llm` is set, message bodies are included in the prompt. See the relevant LLM provider integration doc (e.g., `docs/integrations/anthropic.md`) for data-handling details.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `MCP server failed to start` | `npx` not on PATH or package install failed | Run `npx @gongrzhe/server-gmail-autoauth-mcp` standalone to verify |
| `tool call failed: 401` | OAuth token expired or revoked | Re-run `npx @gongrzhe/server-gmail-autoauth-mcp auth` |
| `[mcp-email] could not parse MCP reply` | Known schema gap | See schema gap note above; a normalising wrapper is needed for production use |
| `No MCP servers configured` | `mcp-config.json` not found | Copy `mcp-config.json.example` to `mcp-config.json` in your working directory |

## Package reference

https://www.npmjs.com/package/@gongrzhe/server-gmail-autoauth-mcp
