ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS mfa_secret_encrypted TEXT;
ALTER TABLE platform_users ADD COLUMN IF NOT EXISTS mfa_last_used_step BIGINT;

CREATE TABLE platform_mfa_enrollments (
    platform_user_id UUID PRIMARY KEY REFERENCES platform_users(id) ON DELETE CASCADE,
    secret_encrypted TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform_mfa_recovery_codes (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(platform_user_id, code_hash)
);
CREATE INDEX ix_platform_mfa_recovery_codes_user ON platform_mfa_recovery_codes(platform_user_id, consumed_at);

CREATE TABLE platform_login_challenges (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_platform_login_challenges_expiry ON platform_login_challenges(expires_at);
