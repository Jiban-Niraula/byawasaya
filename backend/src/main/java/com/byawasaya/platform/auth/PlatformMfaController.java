package com.byawasaya.platform.auth;

import com.byawasaya.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/mfa")
public class PlatformMfaController {
    private final PlatformMfaService mfa;
    private final PlatformUserRepository users;
    private final PasswordMfaService passwordMfa;
    private final AuditService audit;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public PlatformMfaController(PlatformMfaService mfa, PlatformUserRepository users, PasswordMfaService passwordMfa, AuditService audit,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.mfa = mfa;
        this.passwordMfa = passwordMfa;
        this.users = users;
        this.audit = audit;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @GetMapping("/status")
    public AuthDtos.MfaStatus status(Authentication authentication) {
        return mfa.status(authentication.getName());
    }

    @PostMapping("/enrollment")
    public AuthDtos.MfaEnrollment beginEnrollment(@Valid @RequestBody AuthDtos.MfaEnrollmentRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        try {
            return mfa.beginEnrollment(authentication.getName(), request.password(), servletRequest.getRemoteAddr());
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64"))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Platform MFA encryption is not configured.");
            throw ex;
        }
    }

    @PostMapping("/enable")
    public AuthDtos.MfaRecoveryCodes enable(@Valid @RequestBody AuthDtos.MfaEnableRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return new AuthDtos.MfaRecoveryCodes(mfa.completeEnrollment(authentication.getName(), request.code(), servletRequest));
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(@Valid @RequestBody AuthDtos.MfaDisableRequest request,
            Authentication authentication, HttpServletRequest servletRequest, HttpServletResponse response) {
        mfa.disable(authentication.getName(), request.password(), request.code(), servletRequest.getRemoteAddr(), servletRequest);
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        return ResponseEntity.ok(Map.of("message", "Authenticator removed. Sign in again with email verification."));
    }

    @PostMapping("/verify-login")
    public ResponseEntity<AuthDtos.LoginResponse> verifyLogin(@Valid @RequestBody AuthDtos.MfaLoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        try {
            TokenService.IssuedSession issued = passwordMfa.hasChallenge(request.challengeToken())
                    ? passwordMfa.verify(request.challengeToken(), request.code(), servletRequest)
                    : mfa.verifyLogin(request.challengeToken(), request.code(), servletRequest);
            TokenService.TokenPrincipal principal = issued.principal();
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.value(), Duration.ofSeconds(issued.expiresIn())).toString());
            audit.record(principal.username(), principal.role(), null, "PLATFORM_MFA_LOGIN_SUCCEEDED", "AUTHENTICATION", null, "SUCCESS", "{}", servletRequest);
            boolean enrolled = users.findByUsernameIgnoreCase(principal.username()).map(PlatformUser::isMfaEnabled).orElse(false);
            AuthDtos.UserInfo user = new AuthDtos.UserInfo(principal.username(), principal.role(), null, null, null, true, enrolled);
            return ResponseEntity.ok(new AuthDtos.LoginResponse(issued.expiresIn(), user, false, null, enrolled ? "TOTP" : "EMAIL"));
        } catch (ResponseStatusException ex) {
            audit.record(null, "UNKNOWN", null, "PLATFORM_MFA_LOGIN_FAILED", "AUTHENTICATION", null, "FAILED", "{}", servletRequest);
            throw ex;
        }
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return SessionCookieFactory.create(BearerTokenFilter.COOKIE_NAME, value, maxAge, cookieSecure, cookieSameSite, "");
    }
}
