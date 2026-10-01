INSERT INTO customer (
    customer_id,
    status,
    max_approved_amount,
    current_approved_amount
)
VALUES
    ('CLI-1001', 'ELIGIBLE', 10000000, 0),
    ('CLI-1002', 'BLOCKED', 8000000, 0),
    ('CLI-2001', 'ELIGIBLE', 15000000, 0)
    ON CONFLICT (customer_id) DO NOTHING;
