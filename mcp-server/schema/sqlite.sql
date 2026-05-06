-- SQLite schema for the Sales AI MCP server.
-- Mirrors the shape of samples/customer-profile.json from the engine.
--
-- Run via:
--   java -cp "lib/*;out" com.example.salesai.mcp.SeedData \
--        --db jdbc:sqlite:mcp-server/demo.db \
--        --schema mcp-server/schema/sqlite.sql \
--        --seed   mcp-server/samples/seed.sql

CREATE TABLE IF NOT EXISTS customers (
    id                    TEXT PRIMARY KEY,
    primary_email         TEXT NOT NULL UNIQUE,
    display_name          TEXT NOT NULL,
    company               TEXT,
    tier                  TEXT,
    industry              TEXT,
    country               TEXT,
    preferred_language    TEXT,
    account_manager       TEXT,
    contract_status       TEXT,
    contract_renewal_date TEXT,
    payment_status        TEXT,
    lifetime_value_usd    INTEGER
);

CREATE INDEX IF NOT EXISTS idx_customers_email
    ON customers (primary_email);

CREATE TABLE IF NOT EXISTS orders (
    order_id     TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    ordered_on   TEXT,
    amount_usd   INTEGER,
    status       TEXT,
    note         TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_customer
    ON orders (customer_id);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id    TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    opened_on    TEXT,
    priority     TEXT,
    summary      TEXT,
    status       TEXT NOT NULL DEFAULT 'OPEN'
);

CREATE INDEX IF NOT EXISTS idx_tickets_customer
    ON support_tickets (customer_id);

CREATE TABLE IF NOT EXISTS customer_notes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    note         TEXT NOT NULL,
    position     INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notes_customer
    ON customer_notes (customer_id);
