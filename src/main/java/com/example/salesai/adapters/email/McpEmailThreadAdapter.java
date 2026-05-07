package com.example.salesai.adapters.email;

import com.example.salesai.adapters.MiniJson;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.mcp.client.McpClient;
import com.example.salesai.ports.EmailThreadPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EmailThreadPort implementation that fetches threads through a spawned
 * MCP server (Gmail or Outlook). The MCP server is responsible for OAuth;
 * this adapter only speaks JSON tool calls.
 *
 * <p>Expected MCP tool reply schema (a JSON array of thread objects):
 *
 * <pre>
 *   [{ "thread_id":"...", "subject":"...", "messages":[
 *      { "message_id":"...", "from":"...", "to":["..."],
 *        "sent_at":"ISO-8601", "direction":"INBOUND"|"OUTBOUND", "body":"..." }
 *   ]}]
 * </pre>
 *
 * <p>Real MCP servers (Gmail, Outlook) don't return exactly this shape
 * out of the box. Production deployments will wrap them with a thin
 * normalising MCP server (Phase 5+) or feed query strings the underlying
 * MCP supports and post-filter.
 */
public final class McpEmailThreadAdapter implements EmailThreadPort {

    private static final long DEFAULT_TIMEOUT_MS = 15_000;

    private final McpClient mcp;
    private final EmailMcpToolMapping mapping;

    public McpEmailThreadAdapter(McpClient mcp, EmailMcpToolMapping mapping) {
        this.mcp = mcp;
        this.mapping = mapping;
    }

    @Override
    public Optional<EmailThread> loadLatestForCustomer(String customerEmail) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "from:" + customerEmail);
        args.put("max_results", 5);

        String text;
        try {
            text = mcp.callToolText(mapping.searchToolName(), args, DEFAULT_TIMEOUT_MS);
        } catch (IOException e) {
            System.err.println("[mcp-email] tool call failed: " + e.getMessage());
            return Optional.empty();
        }

        if (text == null || text.isBlank() || "[]".equals(text.trim())) {
            System.err.println("[mcp-email] no thread found for " + customerEmail
                + " (server returned " + (text == null ? "null" : "empty") + ")");
            return Optional.empty();
        }

        try {
            List<?> threads = (List<?>) MiniJson.parse(text);
            if (threads.isEmpty()) return Optional.empty();
            return Optional.of(toThread(threads.get(0), customerEmail));
        } catch (RuntimeException e) {
            System.err.println("[mcp-email] could not parse MCP reply: "
                + e.getMessage() + "; reply=" + truncate(text, 200));
            return Optional.empty();
        }
    }

    private static EmailThread toThread(Object raw, String customerEmail) {
        Map<String, Object> m = MiniJson.asObject(raw);
        String threadId = MiniJson.asString(m.getOrDefault("thread_id", ""));
        String subject = MiniJson.asString(m.getOrDefault("subject", ""));
        List<?> rawMsgs = (List<?>) m.getOrDefault("messages", List.of());
        List<EmailMessage> msgs = new ArrayList<>(rawMsgs.size());
        for (Object r : rawMsgs) msgs.add(toMessage(r));
        return new EmailThread(threadId, subject, customerEmail, msgs);
    }

    private static EmailMessage toMessage(Object raw) {
        Map<String, Object> m = MiniJson.asObject(raw);
        String messageId = MiniJson.asString(m.getOrDefault("message_id", ""));
        String from = MiniJson.asString(m.getOrDefault("from", ""));
        List<String> to = toStringList(m.get("to"));
        String sentAt = MiniJson.asString(m.getOrDefault("sent_at", ""));
        String direction = MiniJson.asString(m.getOrDefault("direction", ""));
        String body = MiniJson.asString(m.getOrDefault("body", ""));
        return new EmailMessage(messageId, from, to, sentAt, direction, body);
    }

    private static List<String> toStringList(Object o) {
        if (o == null) return List.of();
        if (o instanceof String s) return List.of(s);
        java.util.List<?> raw = (java.util.List<?>) o;
        List<String> r = new ArrayList<>(raw.size());
        for (Object x : raw) r.add(String.valueOf(x));
        return r;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
