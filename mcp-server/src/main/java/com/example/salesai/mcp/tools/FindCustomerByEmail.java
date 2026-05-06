package com.example.salesai.mcp.tools;

import com.example.salesai.mcp.db.QueryRunner;
import com.example.salesai.mcp.protocol.MiniJson;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;
import com.example.salesai.mcp.protocol.JsonRpc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Look up a single customer profile by primary email. Case-insensitive,
 * exact match. Returns {@code null} when no row matches — never a list,
 * never a partial match. This is the safest of the four tools because
 * the LLM cannot influence anything but the email parameter.
 */
public final class FindCustomerByEmail implements Tool {

    private static final String SQL = """
            SELECT id, primary_email, display_name, company, tier,
                   industry, country, preferred_language, account_manager,
                   contract_status, contract_renewal_date, payment_status,
                   lifetime_value_usd
            FROM customers
            WHERE LOWER(primary_email) = LOWER(?)
            """;

    private final QueryRunner runner;

    public FindCustomerByEmail(QueryRunner runner) {
        this.runner = runner;
    }

    @Override
    public String name() { return "customer.findByEmail"; }

    @Override
    public String description() {
        return "Find one customer by their primary email address (exact match, "
                + "case-insensitive). Returns the customer profile including tier, "
                + "contract status, payment status, and lifetime value.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> emailProp = new LinkedHashMap<>();
        emailProp.put("type", "string");
        emailProp.put("description", "Primary email address of the customer.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("email", emailProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("email"));
        return schema;
    }

    @Override
    public Object call(Map<String, Object> arguments) {
        String email = MiniJson.asString(arguments.get("email"));
        if (email == null || email.isBlank()) {
            throw new RpcException(JsonRpc.INVALID_PARAMS,
                    "'email' is required");
        }
        Map<String, Object> row = runner.queryOne(SQL, email.trim());
        if (row == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("found", false);
            empty.put("email", email);
            return empty;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("found", true);
        out.put("customer", row);
        return out;
    }
}
