ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS full_name VARCHAR(120);
ALTER TABLE businesses ALTER COLUMN plan TYPE VARCHAR(40);
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS trial_starts_at TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS approved_by VARCHAR(180);
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS review_notes VARCHAR(2000);
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS logo_asset_id UUID;

CREATE TABLE IF NOT EXISTS tenant_applications (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    owner_account_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    citizenship_number VARCHAR(100),
    business_type VARCHAR(50) NOT NULL DEFAULT 'RETAIL',
    pan_vat_number VARCHAR(100),
    registration_number VARCHAR(120),
    registration_authority VARCHAR(180),
    registered_address VARCHAR(500),
    province VARCHAR(100),
    district VARCHAR(100),
    municipality VARCHAR(120),
    selected_plan_code VARCHAR(40) NOT NULL DEFAULT 'STARTER',
    selected_template_key VARCHAR(40) NOT NULL DEFAULT 'classic',
    application_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    applicant_notes VARCHAR(2000),
    reviewer_notes VARCHAR(2000),
    public_review_notes VARCHAR(2000),
    reviewer_username VARCHAR(180),
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_tenant_app_status_created ON tenant_applications(application_status, created_at DESC);

CREATE TABLE IF NOT EXISTS store_documents (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES tenant_applications(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewer_note VARCHAR(1000),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(180)
);
CREATE INDEX IF NOT EXISTS ix_store_documents_application ON store_documents(application_id);

CREATE TABLE IF NOT EXISTS platform_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    monthly_price_npr NUMERIC(12,2) NOT NULL CHECK (monthly_price_npr >= 0),
    annual_price_npr NUMERIC(12,2) NOT NULL CHECK (annual_price_npr >= 0),
    trial_days INTEGER NOT NULL DEFAULT 14 CHECK (trial_days BETWEEN 0 AND 365),
    max_products INTEGER NOT NULL DEFAULT 100 CHECK (max_products >= 0),
    max_staff INTEGER NOT NULL DEFAULT 2 CHECK (max_staff >= 1),
    features JSONB NOT NULL DEFAULT '{}'::jsonb,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_plans(id, code, name, description, monthly_price_npr, annual_price_npr, trial_days, max_products, max_staff)
VALUES
(gen_random_uuid(), 'STARTER', 'Starter', 'For new businesses getting online.', 2500, 25000, 14, 100, 2),
(gen_random_uuid(), 'PLUS', 'Plus', 'For growing stores that need more room.', 3500, 35000, 14, 500, 5),
(gen_random_uuid(), 'PRO', 'Pro', 'For established teams operating at scale.', 5000, 50000, 14, 5000, 20)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(500),
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    discount_value NUMERIC(12,2) NOT NULL CHECK (discount_value > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    max_redemptions INTEGER,
    max_per_tenant INTEGER NOT NULL DEFAULT 1,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    eligible_plan_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS promo_redemptions (
    id UUID PRIMARY KEY,
    promo_code_id UUID NOT NULL REFERENCES promo_codes(id),
    business_id UUID NOT NULL REFERENCES businesses(id),
    subscription_id UUID,
    discount_amount_npr NUMERIC(12,2) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(promo_code_id, business_id)
);

CREATE TABLE IF NOT EXISTS email_action_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    action_type VARCHAR(30) NOT NULL CHECK (action_type IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'SET_PASSWORD')),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_email_action_tokens_account ON email_action_tokens(account_id, action_type);

CREATE TABLE IF NOT EXISTS oauth_accounts (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(180),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider, provider_subject),
    UNIQUE(account_id, provider)
);

CREATE TABLE IF NOT EXISTS tenant_provisioning (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    table_prefix VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    actor_username VARCHAR(180),
    actor_role VARCHAR(40),
    tenant_id UUID,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(120),
    outcome VARCHAR(30) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_logs_actor ON audit_logs(actor_username, created_at DESC);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    plan_code VARCHAR(40) NOT NULL,
    plan_name_snapshot VARCHAR(100) NOT NULL,
    monthly_price_snapshot NUMERIC(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    trial_starts_at TIMESTAMPTZ,
    trial_ends_at TIMESTAMPTZ,
    current_period_starts_at TIMESTAMPTZ,
    current_period_ends_at TIMESTAMPTZ,
    promo_code_id UUID REFERENCES promo_codes(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_subscriptions_business ON subscriptions(business_id, created_at DESC);
ALTER TABLE tenant_applications ADD COLUMN IF NOT EXISTS promo_code VARCHAR(60);
ALTER TABLE tenant_applications ADD COLUMN IF NOT EXISTS selected_plan_price_npr NUMERIC(12,2);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS discount_amount_npr NUMERIC(12,2) NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS platform_users (
    id UUID PRIMARY KEY,
    username VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(40) NOT NULL DEFAULT 'SUPERADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_users_username_lower ON platform_users(lower(username));
CREATE TABLE IF NOT EXISTS login_attempts (
    key_hash VARCHAR(64) PRIMARY KEY,
    failures INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS idempotency_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_orders_business_idempotency ON customer_orders(business_id,idempotency_key) WHERE idempotency_key IS NOT NULL;

-- No payment gateway is configured in this release; disable misleading online-payment settings.
UPDATE businesses SET online_payment_enabled = FALSE, cod_enabled = TRUE;
