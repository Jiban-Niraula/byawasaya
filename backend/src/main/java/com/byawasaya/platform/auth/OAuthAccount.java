package com.byawasaya.platform.auth;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="oauth_accounts",uniqueConstraints={@UniqueConstraint(name="uk_oauth_provider_subject",columnNames={"provider","provider_subject"}),@UniqueConstraint(name="uk_oauth_account_provider",columnNames={"account_id","provider"})})
public class OAuthAccount {
 @Id @GeneratedValue private UUID id;
 @Column(name="account_id",nullable=false) private UUID accountId;
 @Column(nullable=false,length=30) private String provider;
 @Column(name="provider_subject",nullable=false,length=255) private String providerSubject;
 @Column(name="provider_email",length=180) private String providerEmail;
 @Column(name="linked_at",nullable=false) private Instant linkedAt=Instant.now();
 protected OAuthAccount(){}
 public OAuthAccount(UUID accountId,String provider,String subject,String email){this.accountId=accountId;this.provider=provider;this.providerSubject=subject;this.providerEmail=email;}
 public UUID getAccountId(){return accountId;} public String getProvider(){return provider;} public String getProviderSubject(){return providerSubject;}
}
