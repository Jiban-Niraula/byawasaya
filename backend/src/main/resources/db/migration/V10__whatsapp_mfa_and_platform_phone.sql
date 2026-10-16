ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE platform_login_challenges ADD COLUMN IF NOT EXISTS otp_hash CHAR(64);

CREATE TABLE IF NOT EXISTS password_login_challenges (
    id UUID PRIMARY KEY,
    account_key VARCHAR(220) NOT NULL,
    username VARCHAR(180) NOT NULL,
    role VARCHAR(40) NOT NULL,
    business_id UUID,
    token_hash CHAR(64) NOT NULL UNIQUE,
    otp_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_password_login_challenges_account
    ON password_login_challenges(account_key, expires_at);
