ALTER TABLE products ADD CONSTRAINT uk_products_business_id_id UNIQUE (business_id, id);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    product_id UUID NOT NULL,
    label VARCHAR(140) NOT NULL,
    sku VARCHAR(80),
    retail_price NUMERIC(12,2) CHECK (retail_price IS NULL OR retail_price > 0),
    wholesale_price NUMERIC(12,2) CHECK (wholesale_price IS NULL OR wholesale_price > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_product_variant_business FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_product_variant_product FOREIGN KEY (business_id, product_id) REFERENCES products(business_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uk_product_variant_label ON product_variants(product_id, lower(label));
CREATE UNIQUE INDEX uk_product_variant_sku ON product_variants(business_id, lower(sku)) WHERE sku IS NOT NULL;
CREATE INDEX ix_product_variants_lookup ON product_variants(business_id, product_id, active, sort_order);

ALTER TABLE order_items ADD COLUMN variant_id UUID;
ALTER TABLE order_items ADD COLUMN variant_label VARCHAR(140);
