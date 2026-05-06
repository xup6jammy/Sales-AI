package com.example.salesai.mcp.tools;

import com.example.salesai.mcp.db.QueryRunner;
import com.example.salesai.mcp.protocol.JsonRpc;
import com.example.salesai.mcp.protocol.MiniJson;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * List recent orders for a single customer. Bounded by {@code limit}
 * (default 10, max 50) so the LLM cannot ask for the entire orders
 * table by passing a huge number.
 */
public final class ListOrders implements Tool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private static final String SQL = """
            SELECT order_id, ordered_on, amount_usd, status, note
            FROM orders
            WHERE customer_id = ?
            ORDER BY ordered_on DESC
            LIMIT ?
            """;

    private final QueryRunner runner;

    public ListOrders(QueryRunner runner) { this.runner = runner; }

    @Override public String name() { return "customer.listOrders"; }

    @Override
    public String description() {
        return "List recent orders for one customer, newest first. "
                + "Capped at " + MAX_LIMIT + " rows.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "string");
        idProp.put("description", "Customer id, e.g. 'CUST-1042'.");

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description",
                "Max number of orders to return (1.." + MAX_LIMIT + ", default "
                        + DEFAULT_LIMIT + ").");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("customerId", idProp);
        properties.put("limit", limitProp);

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

        int limit = DEFAULT_LIMIT;
        if (arguments.containsKey("limit") && arguments.get("limit") != null) {
            try {
                limit = (int) Math.min(MAX_LIMIT,
                        Math.max(1, MiniJson.asLong(arguments.get("limit"))));
            } catch (RuntimeException e) {
                throw new RpcException(JsonRpc.INVALID_PARAMS,
                        "'limit' must be a positive integer");
            }
        }

        List<Map<String, Object>> rows = runner.queryAll(SQL, id.trim(), limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customerId", id);
        out.put("count", rows.size());
        out.put("orders", rows);
        return out;
    }
}
