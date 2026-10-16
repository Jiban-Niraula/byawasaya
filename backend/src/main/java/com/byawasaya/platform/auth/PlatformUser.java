package com.byawasaya.platform.auth;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="platform_users")
public class PlatformUser {
 @Id @GeneratedValue private UUID id;
 @Column(nullable=false,unique=true,length=180) private String username;
 @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Column(name="full_name",nullable=false,length=120) private String fullName;
 @Column(nullable=false,length=30) private String phone;
 @Column(nullable=false,length=40) private String role="SUPERADMIN";
 @Column(nullable=false) private boolean enabled=true;
 @Column(name="mfa_enabled",nullable=false) private boolean mfaEnabled=false;
 @Column(name="mfa_secret_encrypted") private String mfaSecretEncrypted;
 @Column(name="mfa_last_used_step") private Long mfaLastUsedStep;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 protected PlatformUser(){}
 public PlatformUser(String username,String passwordHash,String fullName,String role,String phone){this.username=username.trim().toLowerCase();this.passwordHash=passwordHash;this.fullName=fullName;this.role=role;this.phone=phone.trim();}
 public UUID getId(){return id;} public String getUsername(){return username;} public String getPasswordHash(){return passwordHash;} public String getFullName(){return fullName;} public String getRole(){return role;} public String getPhone(){return phone;} public boolean isEnabled(){return enabled;} public boolean isMfaEnabled(){return mfaEnabled;} public String getMfaSecretEncrypted(){return mfaSecretEncrypted;} public Long getMfaLastUsedStep(){return mfaLastUsedStep;} public Instant getCreatedAt(){return createdAt;} public void setEnabled(boolean value){enabled=value;} public void setPasswordHash(String value){passwordHash=value;} public void setMfaEnabled(boolean value){mfaEnabled=value;} public void setMfaSecretEncrypted(String value){mfaSecretEncrypted=value;} public void setMfaLastUsedStep(Long value){mfaLastUsedStep=value;}
}
