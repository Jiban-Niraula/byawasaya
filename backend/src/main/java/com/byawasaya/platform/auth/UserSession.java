package com.byawasaya.platform.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Persistent server-side session. The raw browser credential is never stored. */
@Entity
@Table(name = "user_sessions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_session_token_hash", columnNames = "token_hash")
})
public class UserSession {
    @Id @GeneratedValue private UUID id;
    @Column(name = "account_key", nullable = false, length = 220) private String accountKey;
    @Column(name = "active_account_key", length = 220) private String activeAccountKey;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(nullable = false, length = 180) private String username;
    @Column(nullable = false, length = 30) private String role;
    @Column(name = "business_id") private UUID businessId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revocation_reason", length = 60) private String revocationReason;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "ip_address", length = 64) private String ipAddress;

    protected UserSession() {}
    public UserSession(String accountKey, String tokenHash, String username, String role, UUID businessId, Instant expiresAt,
            String userAgent, String ipAddress) {
        this.accountKey = accountKey;
        this.activeAccountKey = accountKey;
        this.tokenHash = tokenHash;
        this.username = username;
        this.role = role;
        this.businessId = businessId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }
    @PrePersist void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; if (lastSeenAt == null) lastSeenAt = now; }
    public UUID getId() { return id; }
    public String getAccountKey() { return accountKey; }
    public String getActiveAccountKey() { return activeAccountKey; }
    public String getTokenHash() { return tokenHash; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public UUID getBusinessId() { return businessId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public boolean isUsable() { return revokedAt == null && activeAccountKey != null && expiresAt.isAfter(Instant.now()); }
    public void touch() { lastSeenAt = Instant.now(); }
    public void revoke(String reason) { revokedAt = Instant.now(); revocationReason = reason; activeAccountKey = null; }
}
