package com.example.salesai.mcp.tools;

import java.util.Map;

/**
 * One MCP tool. Each implementation is a thin wrapper around a single
 * prepared SQL query. The set of tools is hand-curated and intentionally
 * narrow — there is no generic {@code runSql} tool, and there will not
 * be one. Whitelisting is the safety boundary that the README and
 * SKILL.md both promise; new tools require a code change and review.
 */
public interface Tool {

    /** Tool name in {@code domain.action} form, e.g. {@code customer.findByEmail}. */
    String name();

    /** One-line human description shown to the LLM in {@code tools/list}. */
    String description();

    /**
     * JSON Schema for the {@code arguments} object. Returned as a
     * Map so it can flow through {@link com.example.salesai.mcp.protocol.MiniJson}
     * directly. Keep this small: type, properties, required.
     */
    Map<String, Object> inputSchema();

    /**
     * Execute the tool. Returns a JSON-serializable value (Map / List
     * / String / Number / Boolean / null). The dispatcher wraps it in
     * the MCP {@code content} envelope.
     */
    Object call(Map<String, Object> arguments);
}
