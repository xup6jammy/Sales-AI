-- PostgreSQL schema for the Sales AI MCP server.
-- Tested against Postgres 14+. Run via:
--   psql -U <user> -d sales -f mcp-server/schema/postgres.sql

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
    contract_renewal_date DATE,
    payment_status        TEXT,
    lifetime_value_usd    BIGINT
);

CREATE INDEX IF NOT EXISTS idx_customers_email
    ON customers (primary_email);

CREATE TABLE IF NOT EXISTS orders (
    order_id     TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    ordered_on   DATE,
    amount_usd   BIGINT,
    status       TEXT,
    note         TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_customer
    ON orders (customer_id);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id    TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    opened_on    DATE,
    priority     TEXT,
    summary      TEXT,
    status       TEXT NOT NULL DEFAULT 'OPEN'
);

CREATE INDEX IF NOT EXISTS idx_tickets_customer
    ON support_tickets (customer_id);

CREATE TABLE IF NOT EXISTS customer_notes (
    id           BIGSERIAL   PRIMARY KEY,
    customer_id  TEXT        NOT NULL REFERENCES customers (id),
    note         TEXT        NOT NULL,
    position     INTEGER     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notes_customer
    ON customer_notes (customer_id);
