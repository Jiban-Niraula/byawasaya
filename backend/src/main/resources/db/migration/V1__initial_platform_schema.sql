CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    tenant_tag VARCHAR(12) NOT NULL UNIQUE,
    owner_name VARCHAR(120) NOT NULL,
    owner_email VARCHAR(180),
    phone VARCHAR(30),
    description VARCHAR(1000),
    plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    template_key VARCHAR(40) NOT NULL DEFAULT 'classic',
    primary_color VARCHAR(20) NOT NULL DEFAULT '#2f6f5e',
    cod_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    online_payment_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    advance_payment_percent INTEGER NOT NULL DEFAULT 30,
    delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 150.00,
    minimum_order_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_business_slug UNIQUE (slug)
);

CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    username VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL,
    business_id UUID NOT NULL REFERENCES businesses(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_phone UNIQUE (phone)
);
CREATE INDEX ix_user_accounts_business_id ON user_accounts(business_id);
CREATE UNIQUE INDEX uk_user_username_lower ON user_accounts (lower(username));

CREATE TABLE products (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    name VARCHAR(140) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    sku VARCHAR(80),
    category VARCHAR(80) NOT NULL,
    description VARCHAR(3000),
    retail_price NUMERIC(12,2) NOT NULL,
    wholesale_price NUMERIC(12,2) NOT NULL,
    wholesale_min_quantity INTEGER NOT NULL DEFAULT 5,
    image_url VARCHAR(1000),
    payment_policy VARCHAR(30) NOT NULL,
    advance_payment_percent INTEGER NOT NULL DEFAULT 30,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_product_store_slug UNIQUE (business_id, slug)
);
CREATE INDEX ix_products_business_active ON products(business_id, active);

CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    order_number VARCHAR(30) NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(180),
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    municipality VARCHAR(120),
    ward VARCHAR(10),
    address_line VARCHAR(300) NOT NULL,
    sales_type VARCHAR(20) NOT NULL,
    payment_method VARCHAR(25) NOT NULL,
    payment_status VARCHAR(25) NOT NULL,
    status VARCHAR(25) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    delivery_fee NUMERIC(12,2) NOT NULL,
    advance_amount NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    customer_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_order_number UNIQUE (order_number)
);
CREATE INDEX ix_orders_business_created ON customer_orders(business_id, created_at DESC);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(140) NOT NULL,
    sku VARCHAR(80),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL
);
CREATE INDEX ix_order_items_order_id ON order_items(order_id);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    account_key VARCHAR(220) NOT NULL,
    active_account_key VARCHAR(220),
    token_hash VARCHAR(64) NOT NULL,
    username VARCHAR(180) NOT NULL,
    role VARCHAR(30) NOT NULL,
    business_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(60),
    CONSTRAINT uk_user_session_token_hash UNIQUE(token_hash),
    CONSTRAINT uk_user_session_active_account UNIQUE(active_account_key)
);
CREATE INDEX ix_user_sessions_account_key ON user_sessions(account_key);
CREATE INDEX ix_user_sessions_expiry ON user_sessions(expires_at);
