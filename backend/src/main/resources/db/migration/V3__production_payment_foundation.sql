CREATE TABLE tenant_payment_credentials (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('ESEWA', 'KHALTI')),
    encrypted_configuration TEXT NOT NULL,
    sandbox BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, provider)
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    order_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    store_slug VARCHAR(80) NOT NULL,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('ESEWA', 'KHALTI')),
    payment_method VARCHAR(25) NOT NULL CHECK (payment_method IN ('ONLINE', 'ADVANCE_COD')),
    amount_paisa BIGINT NOT NULL CHECK (amount_paisa > 0),
    currency CHAR(3) NOT NULL DEFAULT 'NPR',
    status VARCHAR(20) NOT NULL CHECK (status IN ('INITIATED', 'PENDING', 'COMPLETED', 'FAILED')),
    provider_reference VARCHAR(180),
    provider_transaction_id VARCHAR(180),
    merchant_order_reference VARCHAR(100) NOT NULL UNIQUE,
    encrypted_provider_configuration TEXT NOT NULL,
    redirect_url VARCHAR(1000),
    redirect_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_payment_attempt_provider_reference
    ON payment_attempts(provider, provider_reference) WHERE provider_reference IS NOT NULL;
CREATE INDEX ix_payment_attempt_order ON payment_attempts(business_id, order_id, created_at DESC);
CREATE INDEX ix_payment_attempt_status ON payment_attempts(status, updated_at);
CREATE UNIQUE INDEX uk_payment_attempt_active_checkout
    ON payment_attempts(business_id, order_id)
    WHERE status IN ('INITIATED','PENDING');

