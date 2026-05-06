package com.example.salesai.mcp.tools;

import com.example.salesai.mcp.protocol.JsonRpc;
import com.example.salesai.mcp.protocol.MiniJson;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the curated set of tools and answers MCP {@code tools/list}
 * and {@code tools/call} requests against them.
 *
 * <p>Adding a new tool is a code change: register it in the constructor
 * and ship a code review. There is no runtime tool installation path —
 * that is the point of the whitelist.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> initial) {
        for (Tool t : initial) {
            if (tools.containsKey(t.name())) {
                throw new IllegalStateException(
                        "Duplicate tool name: " + t.name());
            }
            tools.put(t.name(), t);
        }
    }

    /** Build the {@code result} payload for {@code tools/list}. */
    public Map<String, Object> listResult() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Tool t : tools.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.name());
            entry.put("description", t.description());
            entry.put("inputSchema", t.inputSchema());
            entries.add(entry);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", entries);
        return out;
    }

    /** Dispatch a {@code tools/call} payload to the named tool. */
    public Map<String, Object> callResult(Map<String, Object> params) {
        String name = MiniJson.asString(params.get("name"));
        if (name == null || name.isBlank()) {
            throw new RpcException(JsonRpc.INVALID_PARAMS,
                    "'name' is required");
        }
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new RpcException(JsonRpc.METHOD_NOT_FOUND,
                    "Unknown tool: " + name);
        }
        Map<String, Object> args = params.containsKey("arguments")
                ? MiniJson.asObject(params.get("arguments"))
                : Map.of();

        Object payload = tool.call(args);

        // MCP wraps tool output as content[].text holding the JSON.
        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", MiniJson.write(payload));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of(textBlock));
        out.put("isError", false);
        return out;
    }

    public int size() { return tools.size(); }
}
