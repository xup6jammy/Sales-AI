package com.example.salesai.mcp.tools;

import com.example.salesai.mcp.db.QueryRunner;
import com.example.salesai.mcp.protocol.JsonRpc;
import com.example.salesai.mcp.protocol.MiniJson;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * List currently-open support tickets for one customer. The status
 * filter is hard-coded to {@code OPEN} on the server side; the LLM
 * cannot ask for closed or all-tickets.
 */
public final class ListOpenTickets implements Tool {

    private static final String SQL = """
            SELECT ticket_id, opened_on, priority, summary, status
            FROM support_tickets
            WHERE customer_id = ?
              AND status = 'OPEN'
            ORDER BY opened_on DESC
            """;

    private final QueryRunner runner;

    public ListOpenTickets(QueryRunner runner) { this.runner = runner; }

    @Override public String name() { return "customer.listOpenTickets"; }

    @Override
    public String description() {
        return "List currently-open support tickets for one customer, "
                + "newest first. Closed tickets are not returned.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "string");
        idProp.put("description", "Customer id, e.g. 'CUST-1042'.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("customerId", idProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("customerId"));
        return schema;
    }

    @Override
    public Object call(Map<String, Object> arguments) {
        String id = MiniJson.asString(arguments.get("customerId"));
        if (id == null || id.isBlank()) {
            throw new RpcException(JsonRpc.INVALID_PARAMS,
                    "'customerId' is required");
        }
        List<Map<String, Object>> rows = runner.queryAll(SQL, id.trim());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customerId", id);
        out.put("count", rows.size());
        out.put("tickets", rows);
        return out;
    }
}
