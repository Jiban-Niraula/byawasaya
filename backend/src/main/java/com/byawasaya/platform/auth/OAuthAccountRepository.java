package com.byawasaya.platform.auth;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount,UUID>{Optional<OAuthAccount> findByProviderAndProviderSubject(String provider,String providerSubject);}
