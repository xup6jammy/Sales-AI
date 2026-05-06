package com.example.salesai.adapters;

import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.ports.CustomerContextPort;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Customer-context adapter backed by a JDBC database. Reads the same
 * shape as {@link MockCustomerContextAdapter} but from
 * {@code customers} / {@code orders} / {@code support_tickets} /
 * {@code customer_notes} tables.
 *
 * <p>This is the engine's path to "real database" without going through
 * the MCP server. Schema lives in {@code mcp-server/schema/*.sql} so
 * the engine and MCP server share a single source of truth.
 *
 * <p>The driver jar (sqlite-jdbc / mysql-connector-j / postgresql) is
 * not bundled. Pass it on the classpath, e.g.:
 * <pre>
 *   java -cp "out;mcp-server/lib/*" \
 *        com.example.salesai.SalesAiCli \
 *        --db jdbc:sqlite:mcp-server/demo.db
 * </pre>
 */
public final class JdbcCustomerContextAdapter implements CustomerContextPort {

    private static final String SQL_FIND_BY_EMAIL = """
            SELECT id, primary_email, display_name, company, tier,
                   industry, country, preferred_language, account_manager,
                   contract_status, contract_renewal_date, payment_status,
                   lifetime_value_usd
            FROM customers
            WHERE LOWER(primary_email) = LOWER(?)
            """;

    private static final String SQL_ORDERS = """
            SELECT order_id, ordered_on, amount_usd, status, note
            FROM orders
            WHERE customer_id = ?
            ORDER BY ordered_on DESC
            """;

    private static final String SQL_OPEN_TICKETS = """
            SELECT ticket_id, opened_on, priority, summary
            FROM support_tickets
            WHERE customer_id = ?
              AND status = 'OPEN'
            ORDER BY opened_on DESC
            """;

    private static final String SQL_NOTES = """
            SELECT note
            FROM customer_notes
            WHERE customer_id = ?
            ORDER BY position ASC
            """;

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public JdbcCustomerContextAdapter(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    public JdbcCustomerContextAdapter(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    @Override
    public Optional<CustomerProfile> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        try (Connection c = openConnection()) {
            CustomerProfile profile = loadProfile(c, email.trim());
            return Optional.ofNullable(profile);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "JDBC lookup failed: " + e.getMessage(), e);
        }
    }

    private Connection openConnection() throws SQLException {
        return user == null
                ? DriverManager.getConnection(jdbcUrl)
                : DriverManager.getConnection(jdbcUrl, user, password);
    }

    private CustomerProfile loadProfile(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SQL_FIND_BY_EMAIL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String id = rs.getString("id");
                CommercialHistory history = new CommercialHistory(
                        loadOrders(c, id),
                        loadOpenTickets(c, id),
                        loadNotes(c, id));

                return new CustomerProfile(
                        id,
                        rs.getString("primary_email"),
                        rs.getString("display_name"),
                        rs.getString("company"),
                        rs.getString("tier"),
                        rs.getString("industry"),
                        rs.getString("country"),
                        rs.getString("preferred_language"),
                        rs.getString("account_manager"),
                        rs.getString("contract_status"),
                        rs.getString("contract_renewal_date"),
                        rs.getString("payment_status"),
                        rs.getLong("lifetime_value_usd"),
                        history);
            }
        }
    }

    private List<CommercialHistory.RecentOrder> loadOrders(Connection c, String customerId)
            throws SQLException {
        List<CommercialHistory.RecentOrder> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SQL_ORDERS)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CommercialHistory.RecentOrder(
                            rs.getString("order_id"),
                            rs.getString("ordered_on"),
                            rs.getLong("amount_usd"),
                            rs.getString("status"),
                            rs.getString("note")));
                }
            }
        }
        return out;
    }

    private List<CommercialHistory.SupportTicket> loadOpenTickets(Connection c, String customerId)
            throws SQLException {
        List<CommercialHistory.SupportTicket> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SQL_OPEN_TICKETS)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CommercialHistory.SupportTicket(
                            rs.getString("ticket_id"),
                            rs.getString("opened_on"),
                            rs.getString("priority"),
                            rs.getString("summary")));
                }
            }
        }
        return out;
    }

    private List<String> loadNotes(Connection c, String customerId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SQL_NOTES)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("note"));
                }
            }
        }
        return out;
    }
}
