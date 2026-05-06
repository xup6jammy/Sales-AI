package com.example.salesai.mcp.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Thin wrapper around a single JDBC connection. The MCP server runs
 * one request at a time over stdio, so a connection pool would be
 * over-engineering — but holding the connection here makes it easy to
 * swap in a pool later (HikariCP, c3p0) without touching tool code.
 *
 * <p>Drivers are loaded by JDBC URL prefix:
 * <ul>
 *   <li>{@code jdbc:sqlite:...}     → SQLite (Xerial sqlite-jdbc)</li>
 *   <li>{@code jdbc:mysql://...}    → MySQL Connector/J</li>
 *   <li>{@code jdbc:postgresql://}  → Postgres JDBC</li>
 * </ul>
 *
 * <p>The driver jars are not bundled with the repo; see
 * {@code mcp-server/lib/README.md} for download instructions.
 */
public final class Database implements AutoCloseable {

    private final Connection connection;
    private final String dialect;

    private Database(Connection connection, String dialect) {
        this.connection = connection;
        this.dialect = dialect;
    }

    /** Open a connection from a JDBC URL with no auth (SQLite case). */
    public static Database open(String jdbcUrl) {
        return open(jdbcUrl, null, null);
    }

    /** Open a connection from a JDBC URL with optional credentials. */
    public static Database open(String jdbcUrl, String user, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl is required");
        }
        String dialect = detectDialect(jdbcUrl);
        try {
            Connection c = (user == null)
                    ? DriverManager.getConnection(jdbcUrl)
                    : DriverManager.getConnection(jdbcUrl, user, password);
            c.setAutoCommit(true);
            return new Database(c, dialect);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to open JDBC connection (" + dialect + "): "
                            + e.getMessage(), e);
        }
    }

    public Connection connection() { return connection; }

    /** Returns "sqlite", "mysql", "postgres", or "unknown". */
    public String dialect() { return dialect; }

    @Override
    public void close() {
        try { connection.close(); }
        catch (SQLException ignore) { /* shutting down */ }
    }

    private static String detectDialect(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:sqlite:"))     return "sqlite";
        if (u.startsWith("jdbc:mysql:"))      return "mysql";
        if (u.startsWith("jdbc:postgresql:")) return "postgres";
        return "unknown";
    }
}
