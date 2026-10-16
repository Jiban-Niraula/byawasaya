package com.byawasaya.platform.auth;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="email_action_tokens")
public class EmailActionToken {
 @Id @GeneratedValue private UUID id;
 @Column(name="account_id",nullable=false) private UUID accountId;
 @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
 @Column(name="action_type",nullable=false,length=30) private String actionType;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="consumed_at") private Instant consumedAt;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 protected EmailActionToken(){}
 public EmailActionToken(UUID accountId,String tokenHash,String actionType,Instant expiresAt){this.accountId=accountId;this.tokenHash=tokenHash;this.actionType=actionType;this.expiresAt=expiresAt;}
 public UUID getAccountId(){return accountId;} public String getTokenHash(){return tokenHash;} public String getActionType(){return actionType;} public Instant getExpiresAt(){return expiresAt;} public Instant getConsumedAt(){return consumedAt;} public boolean isUsable(){return consumedAt==null&&expiresAt.isAfter(Instant.now());} public void consume(){consumedAt=Instant.now();}
}
