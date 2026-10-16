ALTER TABLE user_sessions
    DROP CONSTRAINT IF EXISTS uk_user_session_active_account;

ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_user_sessions_active_account_key
    ON user_sessions(active_account_key)
    WHERE active_account_key IS NOT NULL;
