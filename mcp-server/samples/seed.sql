-- Seed data for the MCP server demo. Mirrors samples/customer-profile.json
-- so the same Wei-Ming Chen / Lumora Robotics scenario shows up whether you
-- run the engine in JSON-mock mode or in JDBC-MCP mode.

INSERT INTO customers (
    id, primary_email, display_name, company, tier, industry, country,
    preferred_language, account_manager, contract_status,
    contract_renewal_date, payment_status, lifetime_value_usd
) VALUES (
    'CUST-1042',
    'wm.chen@lumora-robotics.example',
    'Wei-Ming Chen',
    'Lumora Robotics Co., Ltd.',
    'VIP',
    'Industrial automation',
    'TW',
    'zh-TW',
    'Kelly Wu',
    'ACTIVE',
    '2026-08-31',
    'OVERDUE_30D',
    480000
);

INSERT INTO orders (order_id, customer_id, ordered_on, amount_usd, status, note) VALUES
    ('SO-2026-0188', 'CUST-1042', '2026-04-12', 42000, 'DELIVERED',
     'On-time delivery, signed acceptance');

INSERT INTO orders (order_id, customer_id, ordered_on, amount_usd, status, note) VALUES
    ('SO-2026-0231', 'CUST-1042', '2026-04-29', 18500, 'DELAYED',
     'Logistics partner missed ETA by 9 days');

INSERT INTO support_tickets (ticket_id, customer_id, opened_on, priority, summary, status) VALUES
    ('SUP-7781', 'CUST-1042', '2026-04-25', 'HIGH',
     'Vision module misalignment after firmware 4.2 rollout', 'OPEN');

INSERT INTO customer_notes (customer_id, note, position) VALUES
    ('CUST-1042',
     'Customer escalated twice during Q1 — relationship recovered after on-site visit by Kelly.',
     1);

INSERT INTO customer_notes (customer_id, note, position) VALUES
    ('CUST-1042',
     'Procurement lead Joyce is detail-oriented and prefers written confirmation.',
     2);

INSERT INTO customer_notes (customer_id, note, position) VALUES
    ('CUST-1042',
     'VIP tier — any commercial concession requires manager approval per playbook v3.',
     3);
