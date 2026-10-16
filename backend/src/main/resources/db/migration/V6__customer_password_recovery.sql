CREATE TABLE customer_password_reset_tokens (
    id UUID PRIMARY KEY,
    customer_account_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_password_reset_account
    ON customer_password_reset_tokens(customer_account_id, expires_at DESC);
