package com.byawasaya.platform.auth;
import java.util.*; 
import org.springframework.http.HttpStatus; 
import org.springframework.security.access.prepost.PreAuthorize; 
import org.springframework.security.crypto.password.PasswordEncoder; 
import org.springframework.web.bind.annotation.*; 
import org.springframework.web.server.ResponseStatusException; 
import com.byawasaya.platform.audit.AuditService; 
import jakarta.validation.Valid; 
import jakarta.validation.constraints.*;

@RestController @RequestMapping("/api/platform/users") @PreAuthorize("hasRole('SUPERADMIN')")

public class PlatformUserController 
{
 private final PlatformUserRepository users; 
 private final PasswordEncoder encoder; 
 private final TokenService sessions; 
 private final AuditService audit;
 private final PlatformMfaService mfa;

 public PlatformUserController(PlatformUserRepository users,PasswordEncoder encoder,TokenService sessions,AuditService audit,PlatformMfaService mfa)
 {
    this.users=users;
    this.encoder=encoder;
    this.sessions=sessions;
    this.audit=audit;
    this.mfa=mfa;
}

 public record CreateRequest(
    @NotBlank @Email @Size(max=180)String email,
    @NotBlank @Size(max=120)String fullName,
    @NotBlank @Pattern(regexp="\\+?[0-9]{7,15}")String phone,
    @NotBlank @Size(min=16,max=72)String temporaryPassword,
    @NotBlank @Pattern(regexp="PLATFORM_ADMIN")String role)
    {}

 public record UserResponse(
    UUID id,
    String username,
    String fullName,
    String phone,
    String role,
    boolean enabled,
    boolean mfaEnabled,
    java.time.Instant createdAt)
    {
        static UserResponse from(PlatformUser u)
        {
            return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getPhone(),
                u.getRole(),
                u.isEnabled(),
                u.isMfaEnabled(),
                u.getCreatedAt());
        }
    }

 @GetMapping public List<UserResponse> list(){return users.findAllByOrderByCreatedAtDesc().stream().map(UserResponse::from).toList();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)  public UserResponse create(@Valid @RequestBody CreateRequest r,org.springframework.security.core.Authentication auth){if(users.existsByUsernameIgnoreCase(r.email()))throw new ResponseStatusException(HttpStatus.CONFLICT,"A platform account with this email already exists.");PlatformUser u=users.save(new PlatformUser(r.email(),encoder.encode(r.temporaryPassword()),r.fullName().trim(),r.role(),r.phone().trim()));audit.record(auth.getName(),"SUPERADMIN",null,"PLATFORM_USER_CREATED","PLATFORM_USER",u.getId().toString(),"SUCCESS","{}");return UserResponse.from(u);}
 @PatchMapping("/{id}/enabled") public UserResponse enabled(@PathVariable UUID id,@RequestBody Map<String,Boolean> body,org.springframework.security.core.Authentication auth){boolean enabled=Boolean.TRUE.equals(body.get("enabled"));PlatformUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Platform user not found."));if(u.getUsername().equalsIgnoreCase(auth.getName())&&!enabled)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot disable your own account.");u.setEnabled(enabled);u=users.save(u);if(!enabled)sessions.revokeUser(u.getUsername());audit.record(auth.getName(),"SUPERADMIN",null,enabled?"PLATFORM_USER_ENABLED":"PLATFORM_USER_DISABLED","PLATFORM_USER",id.toString(),"SUCCESS","{}");return UserResponse.from(u);}
 @PostMapping("/{id}/revoke-session") public Map<String,String> revokeSession(@PathVariable UUID id,org.springframework.security.core.Authentication auth){PlatformUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Platform user not found."));sessions.revokeUser(u.getUsername());audit.record(auth.getName(),"SUPERADMIN",null,"PLATFORM_SESSIONS_REVOKED","PLATFORM_USER",id.toString(),"SUCCESS","{}");return Map.of("message","All active sessions revoked.");}
 @PostMapping("/{id}/reset-password") public Map<String,String> resetPassword(@PathVariable UUID id,@RequestBody Map<String,String> body,org.springframework.security.core.Authentication auth){String password=body.get("password");if(password==null||password.length()<16||password.length()>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Temporary password must be 16–72 characters.");PlatformUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Platform user not found."));u.setPasswordHash(encoder.encode(password));users.save(u);sessions.revokeUser(u.getUsername());audit.record(auth.getName(),"SUPERADMIN",null,"PLATFORM_USER_PASSWORD_RESET","PLATFORM_USER",id.toString(),"SUCCESS","{}");return Map.of("message","Password reset. The user must sign in again.");}

 @PostMapping("/{id}/mfa/reset") public Map<String,String> resetMfa(@PathVariable UUID id,org.springframework.security.core.Authentication auth,jakarta.servlet.http.HttpServletRequest request){PlatformUser target=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Platform user not found."));if(target.getUsername().equalsIgnoreCase(auth.getName()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot reset your own MFA. Use a recovery code or the account recovery procedure.");mfa.reset(id,auth.getName(),request);return Map.of("message","Authenticator reset. The user can sign in with email OTP and enroll a new authenticator.");}

}
