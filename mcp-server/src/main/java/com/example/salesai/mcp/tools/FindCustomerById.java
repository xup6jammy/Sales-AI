package com.example.salesai.mcp.tools;

import com.example.salesai.mcp.db.QueryRunner;
import com.example.salesai.mcp.protocol.JsonRpc;
import com.example.salesai.mcp.protocol.MiniJson;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Look up a single customer profile by customer id (e.g. {@code CUST-1042}). */
public final class FindCustomerById implements Tool {

    private static final String SQL = """
            SELECT id, primary_email, display_name, company, tier,
                   industry, country, preferred_language, account_manager,
                   contract_status, contract_renewal_date, payment_status,
                   lifetime_value_usd
            FROM customers
            WHERE id = ?
            """;

    private final QueryRunner runner;

    public FindCustomerById(QueryRunner runner) { this.runner = runner; }

    @Override public String name() { return "customer.findById"; }

    @Override
    public String description() {
        return "Find one customer by their customer id (e.g. 'CUST-1042'). "
                + "Returns the customer profile.";
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
        schema.put("required", java.util.List.of("customerId"));
        return schema;
    }

    @Override
    public Object call(Map<String, Object> arguments) {
        String id = MiniJson.asString(arguments.get("customerId"));
        if (id == null || id.isBlank()) {
            throw new RpcException(JsonRpc.INVALID_PARAMS,
                    "'customerId' is required");
        }
        Map<String, Object> row = runner.queryOne(SQL, id.trim());
        if (row == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("found", false);
            empty.put("customerId", id);
            return empty;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("found", true);
        out.put("customer", row);
        return out;
    }
}
