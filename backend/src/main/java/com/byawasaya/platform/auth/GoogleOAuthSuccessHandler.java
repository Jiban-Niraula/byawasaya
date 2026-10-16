package com.byawasaya.platform.auth;

import java.io.IOException; import java.security.SecureRandom; import java.util.Base64; import java.util.UUID;
import jakarta.servlet.ServletException; import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value; import org.springframework.security.core.Authentication; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.oauth2.core.user.OAuth2User; import org.springframework.security.web.authentication.AuthenticationSuccessHandler; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;
import com.byawasaya.platform.business.*;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
 private final OAuthAccountRepository oauthAccounts; private final UserAccountRepository accounts; private final BusinessRepository businesses; private final TokenService sessions; private final PasswordEncoder encoder; private final boolean cookieSecure; private final String sameSite; private final String frontendUrl; private final SecureRandom random=new SecureRandom();
 public GoogleOAuthSuccessHandler(OAuthAccountRepository oauthAccounts,UserAccountRepository accounts,BusinessRepository businesses,TokenService sessions,PasswordEncoder encoder,@Value("${app.auth.cookie-secure:false}")boolean cookieSecure,@Value("${app.auth.cookie-same-site:Lax}")String sameSite,@Value("${app.frontend-url:http://localhost:5173}")String frontendUrl){this.oauthAccounts=oauthAccounts;this.accounts=accounts;this.businesses=businesses;this.sessions=sessions;this.encoder=encoder;this.cookieSecure=cookieSecure;this.sameSite=sameSite;this.frontendUrl=frontendUrl;}
 @Override @Transactional public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws IOException,ServletException{
  OAuth2User user=(OAuth2User)authentication.getPrincipal();String sub=user.getAttribute("sub"),email=user.getAttribute("email"),name=user.getAttribute("name");Boolean verified=user.getAttribute("email_verified");
  if(sub==null||email==null||!Boolean.TRUE.equals(verified)){response.sendRedirect(frontendUrl+"/tenant/login?oauth=verification-required");return;}
  OAuthAccount existingLink=oauthAccounts.findByProviderAndProviderSubject("google",sub).orElse(null);
  UserAccount account;
  if(existingLink!=null){account=accounts.findById(existingLink.getAccountId()).orElse(null);}
  else {account=accounts.findByUsernameIgnoreCase(email.trim().toLowerCase()).orElse(null);if(account!=null&&!account.isEmailVerified()){response.sendRedirect(frontendUrl+"/tenant/login?oauth=verify-email-first");return;}}
  if(account==null){response.sendRedirect(frontendUrl+"/register?oauth=unlinked");return;}
  if(!account.isEnabled()){response.sendRedirect(frontendUrl+"/tenant/login?oauth=account-disabled");return;}
  if(existingLink==null)oauthAccounts.save(new OAuthAccount(account.getId(),"google",sub,email));
  account.setEmailVerified(true);
  Business business=businesses.findById(account.getBusinessId()).orElse(null);if(business==null||business.getStatus()==BusinessStatus.SUSPENDED){response.sendRedirect(frontendUrl+"/tenant/login?oauth=store-unavailable");return;}
  TokenService.IssuedSession issued=sessions.issue("account:"+account.getUsername().toLowerCase(),account.getUsername(),account.getRole(),account.getBusinessId(),request);
  response.addHeader("Set-Cookie",SessionCookieFactory.create(BearerTokenFilter.COOKIE_NAME,issued.value(),java.time.Duration.ofSeconds(issued.expiresIn()),cookieSecure,sameSite,"").toString());
  response.sendRedirect(frontendUrl+(business.getStatus()==BusinessStatus.ACTIVE?"/tenant/dashboard":"/tenant/onboarding"));
 }
}
