ALTER TABLE subscriptions
    ADD COLUMN annual_price_snapshot NUMERIC(12,2);

UPDATE subscriptions s
SET annual_price_snapshot = COALESCE(p.annual_price_npr, s.monthly_price_snapshot * 12)
FROM platform_plans p
WHERE p.code = s.plan_code AND s.annual_price_snapshot IS NULL;

UPDATE subscriptions
SET annual_price_snapshot = monthly_price_snapshot * 12
WHERE annual_price_snapshot IS NULL;

ALTER TABLE subscriptions
    ALTER COLUMN annual_price_snapshot SET NOT NULL;

CREATE SEQUENCE subscription_invoice_number_seq;

CREATE TABLE subscription_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(32) NOT NULL UNIQUE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE RESTRICT,
    business_name_snapshot VARCHAR(120) NOT NULL,
    owner_name_snapshot VARCHAR(120) NOT NULL,
    owner_email_snapshot VARCHAR(180),
    store_code_snapshot VARCHAR(12) NOT NULL,
    plan_code_snapshot VARCHAR(40) NOT NULL,
    plan_name_snapshot VARCHAR(100) NOT NULL,
    billing_cycle VARCHAR(12) NOT NULL CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
    service_period_start DATE NOT NULL,
    service_period_end DATE NOT NULL,
    due_on DATE NOT NULL,
    subtotal_npr NUMERIC(12,2) NOT NULL CHECK (subtotal_npr >= 0),
    discount_npr NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_npr >= 0),
    amount_due_npr NUMERIC(12,2) NOT NULL CHECK (amount_due_npr >= 0),
    status VARCHAR(12) NOT NULL CHECK (status IN ('OPEN','PAID','VOID')),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_by VARCHAR(180) NOT NULL,
    voided_at TIMESTAMPTZ,
    voided_by VARCHAR(180),
    void_reason VARCHAR(500),
    CHECK (service_period_end >= service_period_start),
    CHECK (amount_due_npr = subtotal_npr - discount_npr)
);

CREATE INDEX ix_subscription_invoices_business_issued
    ON subscription_invoices (business_id, issued_at DESC);
CREATE INDEX ix_subscription_invoices_status_due
    ON subscription_invoices (status, due_on);
CREATE UNIQUE INDEX uk_subscription_invoice_active_period
    ON subscription_invoices (subscription_id, service_period_start, service_period_end)
    WHERE status <> 'VOID';

CREATE TABLE subscription_invoice_payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE REFERENCES subscription_invoices(id) ON DELETE RESTRICT,
    amount_npr NUMERIC(12,2) NOT NULL CHECK (amount_npr > 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER','CASH','ESEWA','KHALTI','OTHER')),
    payment_reference VARCHAR(120) NOT NULL,
    notes VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by VARCHAR(180) NOT NULL
);

CREATE INDEX ix_subscription_invoice_payments_received
    ON subscription_invoice_payments (received_at DESC);
