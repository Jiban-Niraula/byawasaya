package com.byawasaya.platform.auth;
import org.springframework.security.core.userdetails.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service;
@Service public class PlatformUserDetailsService implements UserDetailsService {
 private final UserAccountRepository accounts; private final PlatformUserRepository platformUsers;
 public PlatformUserDetailsService(UserAccountRepository accounts,PlatformUserRepository platformUsers,PasswordEncoder encoder){this.accounts=accounts;this.platformUsers=platformUsers;}
 @Override public UserDetails loadUserByUsername(String identifier)throws UsernameNotFoundException{
  String value=identifier==null?"":identifier.trim();
  var platform=platformUsers.findByUsernameIgnoreCase(value);
  if(platform.isPresent()){PlatformUser u=platform.get();return User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles(u.getRole()).disabled(!u.isEnabled()).build();}
  UserAccount account=accounts.findByUsernameIgnoreCase(value).or(()->accounts.findByPhone(value)).orElseThrow(()->new UsernameNotFoundException("User not found"));
  return User.withUsername(account.getUsername()).password(account.getPasswordHash()).roles(account.getRole()).disabled(!account.isEnabled()).build();
 }
}
