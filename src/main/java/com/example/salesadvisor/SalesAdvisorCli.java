package com.example.salesadvisor;

import com.example.salesadvisor.adapters.ConsoleAuditLogAdapter;
import com.example.salesadvisor.adapters.ManualApprovalAdapter;
import com.example.salesadvisor.adapters.MockCustomerContextAdapter;
import com.example.salesadvisor.adapters.MockEmailThreadAdapter;
import com.example.salesadvisor.adapters.NoopCrmAdapter;
import com.example.salesadvisor.adapters.RuleBasedRiskPolicyAdapter;
import com.example.salesadvisor.adapters.TemplateReplyDraftAdapter;
import com.example.salesadvisor.app.AdvisorReportRenderer;
import com.example.salesadvisor.app.AdvisorWorkflow;
import com.example.salesadvisor.domain.AdvisorRequest;
import com.example.salesadvisor.domain.AdvisorResult;

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
 * </ul>
 *
 * <p>Exit codes: 0 on success, 2 on argument error, 3 if the customer
 * is not found.
 */
public final class SalesAdvisorCli {

    private SalesAdvisorCli() {}

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

        // Wire adapters.
        ConsoleAuditLogAdapter audit = new ConsoleAuditLogAdapter(false);
        MockCustomerContextAdapter customers =
                new MockCustomerContextAdapter(customerJson);
        MockEmailThreadAdapter threads =
                new MockEmailThreadAdapter(threadJson);
        RuleBasedRiskPolicyAdapter risk = new RuleBasedRiskPolicyAdapter();
        TemplateReplyDraftAdapter drafts = new TemplateReplyDraftAdapter();
        NoopCrmAdapter crm = new NoopCrmAdapter(audit);
        ManualApprovalAdapter approvals = new ManualApprovalAdapter(audit);

        // Decide which email to advise on. Default to the loaded
        // profile's primaryEmail.
        String email = parsed.email != null
                ? parsed.email
                : customers.defaultEmail().orElse(null);
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
            System.exit(3);
            return;
        }

        System.out.println(new AdvisorReportRenderer().render(result));
    }

    private static String usage() {
        return """
                Usage: java -cp out com.example.salesadvisor.SalesAdvisorCli [options]

                Options:
                  --help                       Show this message and exit.
                  --approve                    Treat the request as manager-approved.
                  --customer-profile <path>    Path to the customer profile JSON
                                               (default: samples/customer-profile.json).
                  --email-thread <path>        Path to the email thread JSON
                                               (default: samples/email-thread.json).
                  --email <addr>               Customer email to advise on
                                               (default: the profile's primaryEmail).
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
            String email
    ) {
        static Args parse(String[] args) {
            boolean help = false;
            boolean approve = false;
            Path customerProfilePath = null;
            Path emailThreadPath = null;
            String email = null;
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
                    default -> throw new IllegalArgumentException(
                            "Unknown argument: " + a);
                }
            }
            return new Args(help, approve, customerProfilePath,
                    emailThreadPath, email);
        }
    }
}
