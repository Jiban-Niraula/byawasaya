package com.byawasaya.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordMfaService {
    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final EmailMfaProvider email;
    private final LoginRateLimitService rateLimit;
    private final TokenService sessions;
    private final SecureRandom random = new SecureRandom();

    public PasswordMfaService(JdbcTemplate jdbc, EmailMfaProvider email, LoginRateLimitService rateLimit,
            TokenService sessions) {
        this.jdbc = jdbc;
        this.email = email;
        this.rateLimit = rateLimit;
        this.sessions = sessions;
    }

    public boolean isEnabled() {
        return email.isEnabled();
    }

    @Transactional
    public String issue(String accountKey, String username, String role, UUID businessId, String phone, String ip) {
        if (username == null || username.isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "An email address is required for sign-in verification.");
        String rateKey = "email-mfa-send:" + accountKey;
        rateLimit.check(rateKey, ip);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        String token = randomToken();
        email.sendOtp(username, otp);
        jdbc.update("DELETE FROM password_login_challenges WHERE account_key=?", accountKey);
        jdbc.update("""
                INSERT INTO password_login_challenges(id,account_key,username,role,business_id,token_hash,otp_hash,expires_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), accountKey, username, role, businessId, sha256(token), sha256(otp),
                OffsetDateTime.ofInstant(Instant.now().plus(LIFETIME), ZoneOffset.UTC));
        return token;
    }

    @Transactional
    public TokenService.IssuedSession verify(String token, String code, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String tokenHash = sha256(token == null ? "" : token);
        var rows = jdbc.query("""
                SELECT account_key,username,role,business_id,otp_hash
                FROM password_login_challenges
                WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now()
                FOR UPDATE
                """, (rs, row) -> new Challenge(rs.getString("account_key"), rs.getString("username"),
                rs.getString("role"), rs.getObject("business_id", UUID.class), rs.getString("otp_hash")), tokenHash);
        if (rows.isEmpty()) throw invalid();
        Challenge challenge = rows.getFirst();
        String rateKey = "email-mfa-login:" + challenge.accountKey();
        rateLimit.check(rateKey, ip);
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(challenge.otpHash()), HexFormat.of().parseHex(sha256(code)))) {
            rateLimit.failure(rateKey, ip);
            throw invalid();
        }

        if (jdbc.update("UPDATE password_login_challenges SET consumed_at=now() WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now()", tokenHash) != 1)
            throw invalid();
        rateLimit.success(rateKey, ip);
        return sessions.issue(challenge.accountKey(), challenge.username(), challenge.role(), challenge.businessId(), request);
    }

    public boolean hasChallenge(String token) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM password_login_challenges WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now())",
                Boolean.class, sha256(token == null ? "" : token)));
    }

    private String randomToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "The sign-in challenge or verification code is invalid or expired.");
    }

    private record Challenge(String accountKey, String username, String role, UUID businessId, String otpHash) {}
}
