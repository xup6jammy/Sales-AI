package com.example.salesai.adapters.email;

/**
 * Per-provider MCP tool naming. The Gmail and Outlook MCP servers
 * expose semantically equivalent tools under different names; this enum
 * is the single place that knowledge lives so the rest of the engine
 * can stay provider-agnostic.
 *
 * <p>Tool names track the most popular open-source MCP servers as of
 * 2026-Q2: {@code @gongrzhe/server-gmail-autoauth-mcp} for Gmail and
 * {@code mcp-server-outlook} for Outlook. If those evolve, update here.
 */
public enum EmailMcpToolMapping {

    GMAIL("search_emails", "read_email"),
    OUTLOOK("list-messages", "get-message");

    private final String searchToolName;
    private final String getMessageToolName;

    EmailMcpToolMapping(String searchToolName, String getMessageToolName) {
        this.searchToolName = searchToolName;
        this.getMessageToolName = getMessageToolName;
    }

    public String searchToolName() { return searchToolName; }
    public String getMessageToolName() { return getMessageToolName; }

    public static EmailMcpToolMapping fromConfigName(String name) {
        if (name == null) throw new IllegalArgumentException("null provider");
        return switch (name.toLowerCase()) {
            case "gmail" -> GMAIL;
            case "outlook" -> OUTLOOK;
            default -> throw new IllegalArgumentException(
                "unknown email provider: " + name);
        };
    }
}
