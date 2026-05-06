-- MySQL schema for the Sales AI MCP server.
-- Tested against MySQL 8.0+. Run via:
--   mysql -u <user> -p sales < mcp-server/schema/mysql.sql

CREATE TABLE IF NOT EXISTS customers (
    id                    VARCHAR(64)  NOT NULL,
    primary_email         VARCHAR(255) NOT NULL,
    display_name          VARCHAR(255) NOT NULL,
    company               VARCHAR(255),
    tier                  VARCHAR(32),
    industry              VARCHAR(64),
    country               VARCHAR(8),
    preferred_language    VARCHAR(16),
    account_manager       VARCHAR(128),
    contract_status       VARCHAR(32),
    contract_renewal_date DATE,
    payment_status        VARCHAR(32),
    lifetime_value_usd    BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_customers_email (primary_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    order_id     VARCHAR(64)  NOT NULL,
    customer_id  VARCHAR(64)  NOT NULL,
    ordered_on   DATE,
    amount_usd   BIGINT,
    status       VARCHAR(32),
    note         TEXT,
    PRIMARY KEY (order_id),
    KEY idx_orders_customer (customer_id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id    VARCHAR(64) NOT NULL,
    customer_id  VARCHAR(64) NOT NULL,
    opened_on    DATE,
    priority     VARCHAR(16),
    summary      TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    PRIMARY KEY (ticket_id),
    KEY idx_tickets_customer (customer_id),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_notes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id  VARCHAR(64) NOT NULL,
    note         TEXT        NOT NULL,
    position     INT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notes_customer (customer_id),
    CONSTRAINT fk_notes_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
