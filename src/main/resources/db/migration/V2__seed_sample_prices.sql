INSERT INTO price (product_id, amount, currency, active, updated_at)
VALUES
    (1001, 249.9000, 'EUR', TRUE, NOW()),
    (1002, 129.5000, 'EUR', TRUE, NOW()),
    (1003, 89.0000, 'EUR', TRUE, NOW()),
    (1004, 159.0000, 'EUR', TRUE, NOW()),
    (1005, 99.9000, 'EUR', TRUE, NOW()),
    (1006, 179.0000, 'EUR', TRUE, NOW())
ON CONFLICT (product_id) DO UPDATE
SET
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    active = EXCLUDED.active,
    updated_at = EXCLUDED.updated_at;
