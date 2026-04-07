ALTER TABLE price
    ADD COLUMN IF NOT EXISTS price_list_code VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN IF NOT EXISTS customer_group_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS compare_at_amount NUMERIC(15,4),
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ;

UPDATE price
SET price_list_code = 'DEFAULT'
WHERE price_list_code IS NULL OR price_list_code = '';

DROP INDEX IF EXISTS uk_price_product_variant;
CREATE UNIQUE INDEX IF NOT EXISTS uk_price_scope_context
    ON price(product_id, variant_key, price_list_code, COALESCE(customer_group_code, ''));

CREATE INDEX IF NOT EXISTS idx_price_product_context
    ON price(product_id, variant_key, price_list_code, customer_group_code, priority);
