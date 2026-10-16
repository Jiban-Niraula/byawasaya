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

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class UserAccount {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 180) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(length = 30, unique = true) private String phone;
    @Column(nullable = false, length = 30) private String role;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified = false;
    @Column(name = "full_name", length = 120) private String fullName;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    protected UserAccount() {}
    public UserAccount(String username, String passwordHash, String role, UUID businessId) {
        this(username, passwordHash, role, businessId, null);
    }
    public UserAccount(String username, String passwordHash, String role, UUID businessId, String phone) {
        this.username = username.toLowerCase().trim(); this.passwordHash = passwordHash; this.role = role; this.businessId = businessId;
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public UUID getBusinessId() { return businessId; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getFullName() { return fullName; }
    public void setEmailVerified(boolean value) { this.emailVerified = value; }
    public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
}
    public void setFullName(String value) { this.fullName = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
