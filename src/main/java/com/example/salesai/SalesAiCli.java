package com.example.salesai;

import com.example.salesai.adapters.ConsoleAuditLogAdapter;
import com.example.salesai.adapters.JdbcCustomerContextAdapter;
import com.example.salesai.adapters.ManualApprovalAdapter;
import com.example.salesai.adapters.MockCustomerContextAdapter;
import com.example.salesai.adapters.MockEmailThreadAdapter;
import com.example.salesai.adapters.NoopCrmAdapter;
import com.example.salesai.adapters.RuleBasedRiskPolicyAdapter;
import com.example.salesai.adapters.TemplateReplyDraftAdapter;
import com.example.salesai.app.AdvisorReportRenderer;
import com.example.salesai.app.AdvisorWorkflow;
import com.example.salesai.domain.AdvisorRequest;
import com.example.salesai.domain.AdvisorResult;
import com.example.salesai.ports.CustomerContextPort;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point. Wires the in-memory adapters together and prints
 * the report to stdout.
 *
 * <p>Recognised arguments:
 * <ul>
 *   <li>{@code --help} — print usage and exit 0.</li>
 *   <li>{@code --approve} — pretend the manager has approved.</li>
 *   <li>{@code --customer-profile <path>} — override the default JSON.</li>
 *   <li>{@code --email-thread <path>} — override the default JSON.</li>
 *   <li>{@code --email <addr>} — override the default email.</li>
 *   <li>{@code --db <jdbc-url>} — load customer profile from a JDBC database
 *       instead of JSON. See {@code mcp-server/schema/} for table layout.</li>
 *   <li>{@code --db-user <name>} / {@code --db-password <pw>} — credentials.</li>
 *   <li>{@code --email-mcp <provider>} — use a spawned MCP server (gmail/outlook).</li>
 *   <li>{@code --mcp-config <path>} — override the default mcp-config.json location.</li>
 * </ul>
 *
 * <p>Exit codes: 0 on success, 2 on argument error, 3 if the customer
 * is not found.
 */
public final class SalesAiCli {

    private SalesAiCli() {}

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
            System.out.println(usage());
            return;
        }

        Path customerJson = parsed.customerProfilePath != null
                ? parsed.customerProfilePath
                : Paths.get("samples", "customer-profile.json");
        Path threadJson = parsed.emailThreadPath != null
                ? parsed.emailThreadPath
                : Paths.get("samples", "email-thread.json");

        // Wire adapters. The customer-context adapter switches on --db.
        ConsoleAuditLogAdapter audit = new ConsoleAuditLogAdapter(false);

        CustomerContextPort customers;
        String defaultEmail;
        if (parsed.dbUrl != null) {
            customers = new JdbcCustomerContextAdapter(
                    parsed.dbUrl, parsed.dbUser, parsed.dbPassword);
            // In DB mode the user must specify --email; there is no
            // single "default" customer.
            defaultEmail = null;
        } else {
            MockCustomerContextAdapter mock = new MockCustomerContextAdapter(customerJson);
            customers = mock;
            defaultEmail = mock.defaultEmail().orElse(null);
        }

        // Email source: --email-mcp picks an external MCP server (gmail/outlook),
        // otherwise default to the local mock adapter (reads JSON file).
        com.example.salesai.ports.EmailThreadPort threads;
        com.example.salesai.mcp.client.McpClient emailMcpClient = null;
        String emailMcp = parsed.emailMcp();
        if (emailMcp == null || "mock".equals(emailMcp)) {
            threads = new MockEmailThreadAdapter(threadJson);
        } else {
            java.nio.file.Path cfgPath = parsed.mcpConfigPath() != null
                    ? parsed.mcpConfigPath()
                    : Paths.get("mcp-config.json");
            var cfgs = com.example.salesai.mcp.client.McpConfigLoader.load(cfgPath);
            var serverCfg = cfgs.get(emailMcp);
            if (serverCfg == null) {
                System.err.println("No MCP server named '" + emailMcp
                        + "' in " + cfgPath
                        + " (available: " + cfgs.keySet() + ")");
                System.exit(2);
                return;
            }
            try {
                emailMcpClient = com.example.salesai.mcp.client.McpClient.spawn(serverCfg);
                emailMcpClient.initialize(15_000);
            } catch (java.io.IOException ioe) {
                System.err.println("Failed to start MCP server '" + emailMcp
                        + "': " + ioe.getMessage());
                if (emailMcpClient != null) emailMcpClient.close();
                System.exit(2);
                return;
            }
            var mapping = com.example.salesai.adapters.email
                    .EmailMcpToolMapping.fromConfigName(emailMcp);
            threads = new com.example.salesai.adapters.email
                    .McpEmailThreadAdapter(emailMcpClient, mapping);
        }
        RuleBasedRiskPolicyAdapter risk = new RuleBasedRiskPolicyAdapter();
        TemplateReplyDraftAdapter drafts = new TemplateReplyDraftAdapter();
        NoopCrmAdapter crm = new NoopCrmAdapter(audit);
        ManualApprovalAdapter approvals = new ManualApprovalAdapter(audit);

        // Decide which email to advise on. Default to the loaded
        // profile's primaryEmail (mock mode only).
        String email = parsed.email != null ? parsed.email : defaultEmail;
        if (email == null) {
            System.err.println(
                    "Could not infer a default email; pass --email <addr>.");
            System.exit(2);
            return;
        }

        AdvisorWorkflow workflow = new AdvisorWorkflow(
                customers, threads, risk, drafts, crm, approvals, audit);
        AdvisorRequest request = new AdvisorRequest(email, parsed.approve);
        AdvisorResult result = workflow.run(request);

        if (result.customer() == null) {
            System.out.println(new AdvisorReportRenderer().render(result));
            if (emailMcpClient != null) emailMcpClient.close();
            System.exit(3);
            return;
        }

        System.out.println(new AdvisorReportRenderer().render(result));
        if (emailMcpClient != null) emailMcpClient.close();
    }

    private static String usage() {
        return """
                Usage: java -cp out com.example.salesai.SalesAiCli [options]

                Options:
                  --help                       Show this message and exit.
                  --approve                    Treat the request as manager-approved.
                  --customer-profile <path>    Path to the customer profile JSON
                                               (default: samples/customer-profile.json).
                  --email-thread <path>        Path to the email thread JSON
                                               (default: samples/email-thread.json).
                  --email <addr>               Customer email to advise on
                                               (default: the profile's primaryEmail).
                  --db <jdbc-url>              Read the customer profile from a JDBC
                                               database instead of JSON. Requires --email.
                                               Example: jdbc:sqlite:mcp-server/demo.db
                  --db-user <name>             Database user (omit for SQLite).
                  --db-password <pw>           Database password.
                  --email-mcp <provider>       Use an external MCP server for email
                                               instead of the mock JSON file.
                                               Provider name must match an entry in
                                               mcp-config.json (e.g., 'gmail').
                  --mcp-config <path>          Override default mcp-config.json location.
                """;
    }

    // ---------------------------------------------------------------
    //  Args parsing
    // ---------------------------------------------------------------

    private record Args(
            boolean help,
            boolean approve,
            Path customerProfilePath,
            Path emailThreadPath,
            String email,
            String dbUrl,
            String dbUser,
            String dbPassword,
            String emailMcp,                // NEW: gmail|outlook|null(default mock)
            Path mcpConfigPath              // NEW
    ) {
        static Args parse(String[] args) {
            boolean help = false;
            boolean approve = false;
            Path customerProfilePath = null;
            Path emailThreadPath = null;
            String email = null;
            String dbUrl = null;
            String dbUser = null;
            String dbPassword = null;
            String emailMcp = null;
            Path mcpConfigPath = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--help", "-h" -> help = true;
                    case "--approve" -> approve = true;
                    case "--customer-profile" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--customer-profile requires a path");
                        }
                        customerProfilePath = Paths.get(args[++i]);
                    }
                    case "--email-thread" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--email-thread requires a path");
                        }
                        emailThreadPath = Paths.get(args[++i]);
                    }
                    case "--email" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--email requires an address");
                        }
                        email = args[++i];
                    }
                    case "--db" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--db requires a JDBC URL");
                        }
                        dbUrl = args[++i];
                    }
                    case "--db-user" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--db-user requires a value");
                        }
                        dbUser = args[++i];
                    }
                    case "--db-password" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--db-password requires a value");
                        }
                        dbPassword = args[++i];
                    }
                    case "--email-mcp" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--email-mcp requires a provider name (gmail|outlook|mock)");
                        }
                        emailMcp = args[++i];
                    }
                    case "--mcp-config" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException(
                                    "--mcp-config requires a path");
                        }
                        mcpConfigPath = Paths.get(args[++i]);
                    }
                    default -> throw new IllegalArgumentException(
                            "Unknown argument: " + a);
                }
            }
            return new Args(help, approve, customerProfilePath,
                    emailThreadPath, email, dbUrl, dbUser, dbPassword,
                    emailMcp, mcpConfigPath);
        }
    }
}
