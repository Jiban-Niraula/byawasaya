-- Platform logins now accept email OTP as the second factor when TOTP is not enrolled.
-- Revoke pre-policy sessions for these accounts so every continued session must be
-- created by the password-plus-email-OTP login flow.
UPDATE user_sessions
SET active_account_key = NULL,
    revoked_at = now(),
    revocation_reason = 'MFA_POLICY_UPDATE'
WHERE role IN ('SUPERADMIN', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT')
  AND active_account_key IS NOT NULL
  AND revoked_at IS NULL
  AND expires_at > now()
  AND username IN (
      SELECT username
      FROM platform_users
      WHERE mfa_enabled = false
  );
