package com.example.salesai.mcp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convenience for running prepared SELECTs and projecting each row
 * into a {@code Map&lt;String, Object&gt;} ready to JSON-serialize.
 *
 * <p>Tool handlers MUST funnel all SQL through this class. There is no
 * helper for ad-hoc {@code String}-concatenated SQL on purpose: the
 * point of the MCP whitelist design is that LLM-supplied values can
 * only ever land as bound parameters, never as SQL fragments.
 */
public final class QueryRunner {

    private final Database db;

    public QueryRunner(Database db) {
        this.db = db;
    }

    /** Run a prepared SELECT and return the first row, if any. */
    public Map<String, Object> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryAll(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Run a prepared SELECT and return every row. */
    public List<Map<String, Object>> queryAll(String sql, Object... params) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "SQL failed: " + e.getMessage()
                            + "\n  query: " + sql, e);
        }
    }
}
