package com.example.salesai.mcp;

import com.example.salesai.mcp.db.Database;
import com.example.salesai.mcp.db.QueryRunner;
import com.example.salesai.mcp.protocol.JsonRpc;
import com.example.salesai.mcp.protocol.StdioTransport;
import com.example.salesai.mcp.protocol.StdioTransport.RpcException;
import com.example.salesai.mcp.tools.FindCustomerByEmail;
import com.example.salesai.mcp.tools.FindCustomerById;
import com.example.salesai.mcp.tools.ListOpenTickets;
import com.example.salesai.mcp.tools.ListOrders;
import com.example.salesai.mcp.tools.Tool;
import com.example.salesai.mcp.tools.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server entry point. Speaks JSON-RPC 2.0 over stdin/stdout and
 * exposes four whitelisted SQL-backed tools:
 *
 * <ul>
 *   <li>{@code customer.findByEmail}</li>
 *   <li>{@code customer.findById}</li>
 *   <li>{@code customer.listOrders}</li>
 *   <li>{@code customer.listOpenTickets}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java -cp "lib/*;out" com.example.salesai.mcp.SalesMcpServer \
 *        --db jdbc:sqlite:demo.db
 * </pre>
 *
 * <p>Optional flags: {@code --user <name>}, {@code --password <pw>}
 * for non-SQLite drivers.
 */
public final class SalesMcpServer {

    /** Protocol version we advertise. Claude Code negotiates compatibility. */
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_NAME = "sales-ai";
    private static final String SERVER_VERSION = "0.1.0";

    private SalesMcpServer() {}

    public static void main(String[] args) {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException iae) {
            System.err.println("Argument error: " + iae.getMessage());
            System.err.println();
            System.err.println(usage());
            System.exit(2);
            return;
        }
        if (parsed.help) {
            System.err.println(usage());
            return;
        }

        try (Database db = Database.open(parsed.jdbcUrl, parsed.user, parsed.password)) {
            QueryRunner runner = new QueryRunner(db);

            List<Tool> toolList = List.of(
                    new FindCustomerByEmail(runner),
                    new FindCustomerById(runner),
                    new ListOrders(runner),
                    new ListOpenTickets(runner)
            );
            ToolRegistry registry = new ToolRegistry(toolList);

            System.err.println("[mcp] " + SERVER_NAME + " " + SERVER_VERSION
                    + " — " + registry.size() + " tools, dialect=" + db.dialect());

            new StdioTransport(req -> dispatch(req, registry)).run();
        }
    }

    private static Object dispatch(JsonRpc.Request req, ToolRegistry registry) {
        return switch (req.method()) {
            case "initialize" -> initializeResult();
            case "notifications/initialized", "notifications/cancelled" -> null;
            case "ping" -> Map.of();
            case "tools/list" -> registry.listResult();
            case "tools/call" -> registry.callResult(req.params());
            default -> throw new RpcException(JsonRpc.METHOD_NOT_FOUND,
                    "Unknown method: " + req.method());
        };
    }

    private static Map<String, Object> initializeResult() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private static String usage() {
        return """
                Usage: java -cp "lib/*;out" com.example.salesai.mcp.SalesMcpServer [options]

                Required:
                  --db <jdbc-url>        JDBC URL, e.g. jdbc:sqlite:demo.db
                                         or jdbc:mysql://host:3306/sales
                                         or jdbc:postgresql://host:5432/sales

                Optional:
                  --user <name>          Database user (not needed for SQLite).
                  --password <pw>        Database password.
                  --help                 Show this message and exit.

                The server reads JSON-RPC 2.0 from stdin and writes responses to
                stdout. Diagnostics go to stderr. Configure Claude Code via
                .claude/mcp-config.json to spawn this command.
                """;
    }

    private record Args(boolean help, String jdbcUrl, String user, String password) {

        static Args parse(String[] args) {
            boolean help = false;
            String jdbcUrl = null;
            String user = null;
            String password = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--help", "-h" -> help = true;
                    case "--db" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--db requires a JDBC URL");
                        }
                        jdbcUrl = args[++i];
                    }
                    case "--user" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--user requires a value");
                        }
                        user = args[++i];
                    }
                    case "--password" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--password requires a value");
                        }
                        password = args[++i];
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }
            if (!help && jdbcUrl == null) {
                throw new IllegalArgumentException("--db is required");
            }
            return new Args(help, jdbcUrl, user, password);
        }
    }
}
