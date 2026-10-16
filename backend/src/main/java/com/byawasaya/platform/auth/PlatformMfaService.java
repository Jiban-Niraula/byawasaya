package com.byawasaya.platform.auth;

import com.byawasaya.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformMfaService {
    private static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(10);
    private static final Duration LOGIN_CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final int RECOVERY_CODE_COUNT = 10;
    private final JdbcTemplate jdbc;
    private final PlatformUserRepository users;
    private final TokenService sessions;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimitService rateLimit;
    private final PlatformMfaCrypto crypto;
    private final AuditService audit;
    private final EmailMfaProvider email;
    private final SecureRandom random = new SecureRandom();

    public PlatformMfaService(JdbcTemplate jdbc, PlatformUserRepository users, TokenService sessions,
            PasswordEncoder passwordEncoder, LoginRateLimitService rateLimit,
            PlatformMfaCrypto crypto, AuditService audit, EmailMfaProvider email) {
        this.jdbc = jdbc;
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
        this.crypto = crypto;
        this.audit = audit;
        this.email = email;
    }

    @Transactional(readOnly = true)
    public AuthDtos.MfaStatus status(String username) {
        PlatformUser user = platformUser(username);
        Integer remaining = jdbc.queryForObject(
                "SELECT count(*) FROM platform_mfa_recovery_codes WHERE platform_user_id=? AND consumed_at IS NULL",
                Integer.class, user.getId());
        return new AuthDtos.MfaStatus(user.isMfaEnabled(), remaining == null ? 0 : remaining);
    }

    @Transactional
    public String issueChallenge(PlatformUser user) {
        jdbc.update("DELETE FROM platform_login_challenges WHERE platform_user_id=?", user.getId());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        // Accounts without TOTP enrollment use email OTP as their second factor.
        // Once an authenticator is enrolled, use TOTP or a recovery code for platform sign-in.
        String otpHash = null;
        if (!user.isMfaEnabled()) {
            if (!email.isEnabled()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Email OTP is required for this platform account, but email MFA delivery is unavailable.");
            }
            String otp = String.format("%06d", random.nextInt(1_000_000));
            email.sendOtp(user.getUsername(), otp);
            otpHash = sha256(otp);
        }
        jdbc.update("INSERT INTO platform_login_challenges(id,platform_user_id,token_hash,otp_hash,expires_at) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), user.getId(), sha256(token), otpHash,
                OffsetDateTime.ofInstant(Instant.now().plus(LOGIN_CHALLENGE_LIFETIME), ZoneOffset.UTC));
        return token;
    }

    @Transactional
    public AuthDtos.MfaEnrollment beginEnrollment(String username, String password, String ip) {
        PlatformUser user = platformUser(username);
        checkRate("platform-mfa-enroll:" + user.getUsername(), ip);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimit.failure("platform-mfa-enroll:" + user.getUsername(), ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password verification failed.");
        }
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Multi-factor authentication is already enabled.");
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String encrypted = crypto.encrypt(secret, user.getId());
        jdbc.update("DELETE FROM platform_mfa_enrollments WHERE platform_user_id=?", user.getId());
        Instant expiresAt = Instant.now().plus(ENROLLMENT_LIFETIME);
        jdbc.update("INSERT INTO platform_mfa_enrollments(platform_user_id,secret_encrypted,expires_at) VALUES(?,?,?)",
                user.getId(), encrypted, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        rateLimit.success("platform-mfa-enroll:" + user.getUsername(), ip);
        return new AuthDtos.MfaEnrollment(crypto.encodeBase32(secret), crypto.provisioningUri(user.getUsername(), secret), expiresAt);
    }

    @Transactional
    public List<String> completeEnrollment(String username, String submittedCode, HttpServletRequest request) {
        PlatformUser user = platformUser(username);
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Multi-factor authentication is already enabled.");
        String rateKey = "platform-mfa-enroll-verify:" + user.getUsername();
        checkRate(rateKey, request.getRemoteAddr());
        List<String> secrets = jdbc.query("SELECT secret_encrypted FROM platform_mfa_enrollments WHERE platform_user_id=? AND expires_at>now() FOR UPDATE",
                (rs, row) -> rs.getString(1), user.getId());
        if (secrets.isEmpty()) throw new ResponseStatusException(HttpStatus.GONE, "The enrollment expired. Start setup again.");
        byte[] secret = crypto.decrypt(secrets.getFirst(), user.getId());
        Long step = crypto.matchingStep(secret, submittedCode, Instant.now().getEpochSecond(), null);
        if (step == null) {
            rateLimit.failure(rateKey, request.getRemoteAddr());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That authenticator code is invalid or expired.");
        }

        int enabled = jdbc.update("UPDATE platform_users SET mfa_enabled=true,mfa_secret_encrypted=?,mfa_last_used_step=? WHERE id=? AND mfa_enabled=false",
                secrets.getFirst(), step, user.getId());
        if (enabled != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Multi-factor setup changed. Refresh and sign in again.");
        jdbc.update("DELETE FROM platform_mfa_enrollments WHERE platform_user_id=?", user.getId());
        jdbc.update("DELETE FROM platform_mfa_recovery_codes WHERE platform_user_id=?", user.getId());
        List<String> recoveryCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = recoveryCode();
            recoveryCodes.add(code);
            jdbc.update("INSERT INTO platform_mfa_recovery_codes(id,platform_user_id,code_hash) VALUES(?,?,?)",
                    UUID.randomUUID(), user.getId(), sha256(normalizeRecoveryCode(code)));
        }
        rateLimit.success(rateKey, request.getRemoteAddr());
        audit.record(user.getUsername(), user.getRole(), null, "PLATFORM_MFA_ENABLED", "PLATFORM_USER",
                user.getId().toString(), "SUCCESS", "{}", request);
        return List.copyOf(recoveryCodes);
    }

    @Transactional
    public TokenService.IssuedSession verifyLogin(String challengeToken, String submittedCode, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String tokenHash = sha256(challengeToken == null ? "" : challengeToken);
        List<Challenge> found = jdbc.query("""
                SELECT c.platform_user_id,u.username,u.role,u.mfa_secret_encrypted,u.mfa_last_used_step,c.otp_hash
                FROM platform_login_challenges c JOIN platform_users u ON u.id=c.platform_user_id
                WHERE c.token_hash=? AND c.consumed_at IS NULL AND c.expires_at>now()
                  AND u.enabled=true AND (c.otp_hash IS NOT NULL OR u.mfa_enabled=true)
                FOR UPDATE OF c,u
                """, (rs, row) -> new Challenge(rs.getObject("platform_user_id", UUID.class), rs.getString("username"),
                        rs.getString("role"), rs.getString("mfa_secret_encrypted"), rs.getObject("mfa_last_used_step", Long.class),
                        rs.getString("otp_hash")), tokenHash);
        if (found.isEmpty()) throw unauthorizedChallenge();
        Challenge challenge = found.getFirst();
        String rateKey = "platform-mfa-login:" + challenge.username();
        checkRate(rateKey, ip);
        PlatformUser user = users.findById(challenge.userId()).orElseThrow(PlatformMfaService::unauthorizedChallenge);
        boolean emailCode = !user.isMfaEnabled() && challenge.otpHash() != null
                && MessageDigest.isEqual(HexFormat.of().parseHex(challenge.otpHash()), HexFormat.of().parseHex(sha256(submittedCode)));
        if (!emailCode && !consumeSecondFactor(user, challenge.encryptedSecret(), challenge.lastUsedStep(), submittedCode)) {
            rateLimit.failure(rateKey, ip);
            throw unauthorizedChallenge();
        }
        int consumed = jdbc.update("UPDATE platform_login_challenges SET consumed_at=now() WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now()", tokenHash);
        if (consumed != 1) throw unauthorizedChallenge();
        rateLimit.success(rateKey, ip);
        return sessions.issue("platform:" + user.getUsername().toLowerCase(), user.getUsername(), user.getRole(), null, request);
    }

    @Transactional
    public void disable(String username, String password, String submittedCode, String ip, HttpServletRequest request) {
        PlatformUser user = platformUser(username);
        if (!user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Multi-factor authentication is already disabled.");
        String rateKey = "platform-mfa-disable:" + user.getUsername();
        checkRate(rateKey, ip);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimit.failure(rateKey, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password or verification code is incorrect.");
        }
        if (!consumeSecondFactor(user, user.getMfaSecretEncrypted(), user.getMfaLastUsedStep(), submittedCode)) {
            rateLimit.failure(rateKey, ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password or verification code is incorrect.");
        }
        user.setMfaEnabled(false);
        user.setMfaSecretEncrypted(null);
        user.setMfaLastUsedStep(null);
        users.save(user);
        jdbc.update("DELETE FROM platform_mfa_recovery_codes WHERE platform_user_id=?", user.getId());
        jdbc.update("DELETE FROM platform_mfa_enrollments WHERE platform_user_id=?", user.getId());
        sessions.revokeUser(user.getUsername());
        rateLimit.success(rateKey, ip);
        audit.record(user.getUsername(), user.getRole(), null, "PLATFORM_TOTP_DISABLED", "PLATFORM_USER",
                user.getId().toString(), "SUCCESS", "{}", request);
    }

    @Transactional
    public void reset(UUID userId, String actor, HttpServletRequest request) {
        PlatformUser user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform user not found."));
        user.setMfaEnabled(false);
        user.setMfaSecretEncrypted(null);
        user.setMfaLastUsedStep(null);
        users.save(user);
        jdbc.update("DELETE FROM platform_mfa_recovery_codes WHERE platform_user_id=?", user.getId());
        jdbc.update("DELETE FROM platform_mfa_enrollments WHERE platform_user_id=?", user.getId());
        jdbc.update("DELETE FROM platform_login_challenges WHERE platform_user_id=?", user.getId());
        sessions.revokeUser(user.getUsername());
        audit.record(actor, "SUPERADMIN", null, "PLATFORM_MFA_RESET", "PLATFORM_USER", user.getId().toString(), "SUCCESS", "{}", request);
    }

    private boolean consumeSecondFactor(PlatformUser user, String encryptedSecret, Long lastUsedStep, String submittedCode) {
        if (encryptedSecret != null) {
            byte[] secret = crypto.decrypt(encryptedSecret, user.getId());
            Long step = crypto.matchingStep(secret, submittedCode, Instant.now().getEpochSecond(), lastUsedStep);
            if (step != null && jdbc.update("UPDATE platform_users SET mfa_last_used_step=? WHERE id=? AND mfa_enabled=true AND (mfa_last_used_step IS NULL OR mfa_last_used_step<?)",
                    step, user.getId(), step) == 1) return true;
        }
        String normalized = normalizeRecoveryCode(submittedCode);
        if (normalized.isBlank()) return false;
        return jdbc.update("UPDATE platform_mfa_recovery_codes SET consumed_at=now() WHERE platform_user_id=? AND code_hash=? AND consumed_at IS NULL",
                user.getId(), sha256(normalized)) == 1;
    }

    private PlatformUser platformUser(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Platform account not found."));
    }

    private void checkRate(String key, String ip) { rateLimit.check(key, ip); }

    private String recoveryCode() {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        String raw = crypto.encodeBase32(bytes);
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static ResponseStatusException unauthorizedChallenge() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The sign-in challenge or verification code is invalid or expired.");
    }

    private record Challenge(UUID userId, String username, String role, String encryptedSecret, Long lastUsedStep, String otpHash) {}
}
