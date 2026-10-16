package com.byawasaya.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-managed opaque sessions. Token values are only returned to the cookie writer. */
@Service
public class TokenService {
    public static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserSessionRepository sessions;

    public TokenService(UserSessionRepository sessions) { this.sessions = sessions; }

    @Transactional
    public IssuedSession issue(String accountKey, String username, String role, UUID businessId, HttpServletRequest request) {
        String normalizedKey = accountKey.trim().toLowerCase();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String userAgent = request == null ? null : limit(request.getHeader("User-Agent"), 500);
        String ipAddress = request == null ? null : limit(request.getRemoteAddr(), 64);
        UserSession session = new UserSession(normalizedKey, sha256(raw), username, role, businessId,
                Instant.now().plus(SESSION_LIFETIME), userAgent, ipAddress);
        sessions.saveAndFlush(session);
        return new IssuedSession(raw, SESSION_LIFETIME.toSeconds(), toPrincipal(session));
    }

    @Transactional
    public void revokeUser(String username) {
        if (username == null || username.isBlank()) return;
        String normalized=username.trim().toLowerCase();
        revokeAll(sessions.findAllByActiveAccountKey("account:" + normalized), "ACCOUNT_DISABLED");
        revokeAll(sessions.findAllByActiveAccountKey("platform:" + normalized), "ACCOUNT_DISABLED");
    }

    @Transactional
    public void revokeAccountKey(String accountKey, String reason) {
        if (accountKey == null || accountKey.isBlank()) return;
        revokeAll(sessions.findAllByActiveAccountKey(accountKey.trim().toLowerCase()), reason);
    }

    @Transactional
    public void revokeBusiness(UUID businessId) {
        if (businessId == null) return;
        sessions.findAllByBusinessIdAndActiveAccountKeyIsNotNull(businessId)
                .forEach(session -> { session.revoke("TENANT_SUSPENDED"); sessions.save(session); });
    }

    @Transactional
    public void revoke(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) return;
        sessions.findByTokenHash(sha256(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) sessions.save(sessionWithRevocation(session, reason));
        });
    }

    private UserSession sessionWithRevocation(UserSession session, String reason) { session.revoke(reason); return session; }

    private void revokeAll(Iterable<UserSession> activeSessions, String reason) {
        activeSessions.forEach(session -> {
            session.revoke(reason);
            sessions.save(session);
        });
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.strip();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }

    @Transactional(readOnly = true)
    public TokenPrincipal principalFor(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return sessions.findByTokenHash(sha256(rawToken)).filter(UserSession::isUsable).map(this::toPrincipal).orElse(null);
    }

    private TokenPrincipal toPrincipal(UserSession session) {
        return new TokenPrincipal(session.getUsername(), session.getRole(), session.getBusinessId(), session.getExpiresAt(), session.getAccountKey());
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
    public record IssuedSession(String value, long expiresIn, TokenPrincipal principal) {}
    public record TokenPrincipal(String username, String role, UUID businessId, Instant expiresAt, String accountKey) {}
}
