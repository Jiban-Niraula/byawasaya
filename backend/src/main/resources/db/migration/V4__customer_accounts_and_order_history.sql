CREATE TABLE customer_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_customer_accounts_email_lower ON customer_accounts(lower(email));

CREATE TABLE customer_email_verification_tokens (
    id UUID PRIMARY KEY,
    customer_account_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_verification_account ON customer_email_verification_tokens(customer_account_id, expires_at DESC);

ALTER TABLE customer_orders
    ADD COLUMN customer_account_id UUID REFERENCES customer_accounts(id) ON DELETE SET NULL;
CREATE INDEX ix_customer_orders_account_created ON customer_orders(customer_account_id, created_at DESC);

CREATE TABLE customer_order_history (
    id UUID PRIMARY KEY,
    customer_account_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    store_name VARCHAR(120) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    payment_status VARCHAR(25) NOT NULL,
    order_status VARCHAR(25) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(customer_account_id, business_id, order_id)
);
CREATE INDEX ix_customer_order_history_account ON customer_order_history(customer_account_id, created_at DESC);
ALTER TABLE tenant_provisioning ADD COLUMN IF NOT EXISTS last_migrated_at TIMESTAMPTZ;
