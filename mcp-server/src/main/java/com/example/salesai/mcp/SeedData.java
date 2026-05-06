package com.example.salesai.mcp;

import com.example.salesai.mcp.db.Database;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Loads schema and seed data into a fresh database. Use this once
 * before starting {@link SalesMcpServer} for the first time.
 *
 * <pre>
 *   java -cp "lib/*;out" com.example.salesai.mcp.SeedData \
 *        --db jdbc:sqlite:demo.db \
 *        --schema mcp-server/schema/sqlite.sql \
 *        --seed   mcp-server/samples/seed.sql
 * </pre>
 *
 * <p>Defaults: SQLite at {@code mcp-server/demo.db}, sqlite schema,
 * and the bundled seed file.
 */
public final class SeedData {

    private SeedData() {}

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

        String schemaSql = readFile(parsed.schemaPath);
        String seedSql   = readFile(parsed.seedPath);

        try (Database db = Database.open(parsed.jdbcUrl, parsed.user, parsed.password);
             Statement stmt = db.connection().createStatement()) {

            System.out.println("[seed] dialect=" + db.dialect());
            System.out.println("[seed] applying schema: " + parsed.schemaPath);
            execScript(stmt, schemaSql);

            System.out.println("[seed] inserting seed:   " + parsed.seedPath);
            execScript(stmt, seedSql);

            System.out.println("[seed] done.");
        } catch (SQLException e) {
            throw new RuntimeException("seed failed: " + e.getMessage(), e);
        }
    }

    private static String readFile(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new UncheckedIOException("Cannot read " + p, ioe);
        }
    }

    /**
     * Naive {@code ;}-splitter. Sufficient for the schema and seed
     * files in this repo, which contain no semicolons inside quoted
     * strings. Line comments are stripped before splitting so a
     * literal {@code ;} inside a {@code --} comment doesn't cut a
     * statement in half.
     */
    private static void execScript(Statement stmt, String script) throws SQLException {
        String stripped = stripComments(script);
        for (String raw : stripped.split(";")) {
            String sql = raw.trim();
            if (sql.isEmpty()) continue;
            stmt.execute(sql);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            int idx = line.indexOf("--");
            if (idx >= 0) line = line.substring(0, idx);
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String usage() {
        return """
                Usage: java -cp "lib/*;out" com.example.salesai.mcp.SeedData [options]

                Options:
                  --db <jdbc-url>        JDBC URL (default: jdbc:sqlite:mcp-server/demo.db)
                  --schema <path>        Schema SQL file (default: mcp-server/schema/sqlite.sql)
                  --seed <path>          Seed SQL file (default: mcp-server/samples/seed.sql)
                  --user <name>          DB user (not needed for SQLite).
                  --password <pw>        DB password.
                  --help                 Show this message.
                """;
    }

    private record Args(boolean help, String jdbcUrl, Path schemaPath, Path seedPath,
                        String user, String password) {

        static Args parse(String[] args) {
            boolean help = false;
            String jdbcUrl = "jdbc:sqlite:mcp-server/demo.db";
            Path schemaPath = Path.of("mcp-server", "schema", "sqlite.sql");
            Path seedPath   = Path.of("mcp-server", "samples", "seed.sql");
            String user = null;
            String password = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--help", "-h" -> help = true;
                    case "--db"     -> jdbcUrl    = require(args, ++i, "--db");
                    case "--schema" -> schemaPath = Path.of(require(args, ++i, "--schema"));
                    case "--seed"   -> seedPath   = Path.of(require(args, ++i, "--seed"));
                    case "--user"     -> user     = require(args, ++i, "--user");
                    case "--password" -> password = require(args, ++i, "--password");
                    default -> throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }
            return new Args(help, jdbcUrl, schemaPath, seedPath, user, password);
        }

        private static String require(String[] args, int i, String flag) {
            if (i >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[i];
        }
    }
}
