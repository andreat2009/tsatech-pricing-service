ALTER TABLE price
    ADD COLUMN IF NOT EXISTS variant_key VARCHAR(128) NOT NULL DEFAULT '';

ALTER TABLE price DROP CONSTRAINT IF EXISTS price_product_id_key;
DROP INDEX IF EXISTS idx_price_product;

CREATE UNIQUE INDEX IF NOT EXISTS uk_price_product_variant ON price(product_id, variant_key);
CREATE INDEX IF NOT EXISTS idx_price_product ON price(product_id);
