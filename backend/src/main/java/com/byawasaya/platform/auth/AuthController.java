package com.byawasaya.platform.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import com.byawasaya.platform.audit.AuditService;
import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessPlan;
import com.byawasaya.platform.business.BusinessRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService sessions;
    private final UserAccountRepository accounts;
    private final PlatformUserRepository platformUsers;
    private final BusinessRepository businesses;
    private final PlatformMfaService platformMfa;
    private final PasswordMfaService passwordMfa;
    private final LoginRateLimitService rateLimit;
    private final AuditService audit;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(AuthenticationManager manager, TokenService sessions, UserAccountRepository accounts,
            PlatformUserRepository platformUsers, BusinessRepository businesses, PlatformMfaService platformMfa,
            PasswordMfaService passwordMfa,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder, LoginRateLimitService rateLimit,
            AuditService audit,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.authenticationManager = manager; this.sessions = sessions; this.accounts = accounts;
        this.platformUsers = platformUsers;         this.businesses = businesses; this.platformMfa = platformMfa; this.passwordMfa = passwordMfa;
        this.passwordEncoder = passwordEncoder; this.rateLimit = rateLimit; this.audit = audit; this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) { return new AuthDtos.CsrfResponse(token.getToken()); }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String identifier = normalizeIdentifier(request.identifier());
        String ip = servletRequest.getRemoteAddr();
        rateLimit.check(identifier, ip);
        Authentication auth;
        try { auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier, request.password())); }
        catch (org.springframework.security.core.AuthenticationException ex) {
            rateLimit.failure(identifier, ip);
            audit.record(null,"UNKNOWN",null,"AUTH_LOGIN_FAILED","AUTHENTICATION",null,"FAILED","{\"identifierHash\":\""+sha256(identifier)+"\"}",servletRequest);
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid username or password.");
        }
        rateLimit.success(identifier, ip);
        String authenticatedRole = auth.getAuthorities().stream().map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse("STORE_OWNER");
        boolean platformAccount = java.util.Set.of("SUPERADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT").contains(authenticatedRole);
        if ((platformAccount && !request.accountType().equals("PLATFORM")) || (!platformAccount && !request.accountType().equals("TENANT"))) {
            audit.record(auth.getName(),authenticatedRole,null,"AUTH_LOGIN_REJECTED","AUTHENTICATION",null,"DENIED","{\"reason\":\"WORKSPACE_MISMATCH\"}",servletRequest);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account cannot sign in to that workspace.");
        }
        String role = authenticatedRole;
        PlatformUser platformUser = null;
        if (platformAccount) {
            platformUser = platformUsers.findByUsernameIgnoreCase(auth.getName())
                    .filter(PlatformUser::isEnabled)
                    .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid username or password."));
            // Every platform login must pass a platform MFA challenge, regardless of whether
            // tenant email-MFA settings happen to be enabled.
            String challenge = platformMfa.issueChallenge(platformUser);
            audit.record(auth.getName(), role, null, "PLATFORM_MFA_CHALLENGE_ISSUED", "AUTHENTICATION", null, "SUCCESS", "{}", servletRequest);
            return new AuthDtos.LoginResponse(300, null, true, challenge, platformUser.isMfaEnabled() ? "TOTP" : "EMAIL");
        }
        UUID businessId = null;
        String slug = null;
        String storeStatus = null;
        boolean emailVerified = true;
        if (!platformAccount) {
            UserAccount user = accounts.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
            businessId = user.getBusinessId();
            emailVerified = user.isEmailVerified();
            Business business = businesses.findById(businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "The tenant account is not linked to a store."));
            if (business.getStatus() == com.byawasaya.platform.business.BusinessStatus.SUSPENDED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This store account is suspended.");
            }
            slug = business.getSlug();
            storeStatus = business.getStatus().name();
            if (business.getStatus() == com.byawasaya.platform.business.BusinessStatus.ACTIVE && business.getTrialEndsAt() != null && business.getTrialEndsAt().isBefore(java.time.Instant.now())) storeStatus = "TRIAL_EXPIRED";
            if (passwordMfa.isEnabled()) {
                String challenge = passwordMfa.issue("account:" + user.getUsername().toLowerCase(), user.getUsername(), role,
                        user.getBusinessId(), user.getPhone(), ip);
                return new AuthDtos.LoginResponse(300, null, true, challenge);
            }
        }
        String accountKey = (platformAccount ? "platform:" : "account:") + auth.getName().trim().toLowerCase();
        TokenService.IssuedSession issued = sessions.issue(accountKey, auth.getName(), role, businessId, servletRequest);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.value(), Duration.ofSeconds(issued.expiresIn())).toString());
        audit.record(auth.getName(),role,businessId,"AUTH_LOGIN_SUCCEEDED","AUTHENTICATION",null,"SUCCESS","{}",servletRequest);
        return new AuthDtos.LoginResponse(issued.expiresIn(),
                new AuthDtos.UserInfo(auth.getName(), role, businessId, slug, storeStatus, emailVerified,
                        platformUser == null || platformUser.isMfaEnabled()), false, null);
    }

    /** Legacy registration endpoint intentionally disabled so every new store uses the complete application workflow. */
    @PostMapping("/register-store")
    public ResponseEntity<AuthDtos.RegistrationResponse> registerStore(@Valid @RequestBody AuthDtos.RegisterStoreRequest request) {
        throw new ResponseStatusException(HttpStatus.GONE, "Use /api/tenant-applications/register to complete the store application.");
    }

    @GetMapping("/session")
    public AuthDtos.SessionResponse session(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof TokenService.TokenPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid sign-in is required.");
        }
        String role = principal.role();
        Business business = principal.businessId() == null ? null : businesses.findById(principal.businessId()).orElse(null);
        String slug = business == null ? null : business.getSlug();
        String status = business == null ? null : business.getStatus().name();
        if (business != null && business.getStatus() == com.byawasaya.platform.business.BusinessStatus.ACTIVE && business.getTrialEndsAt() != null && business.getTrialEndsAt().isBefore(java.time.Instant.now())) status = "TRIAL_EXPIRED";
        boolean verified = accounts.findByUsernameIgnoreCase(principal.username()).map(UserAccount::isEmailVerified).orElse(true);
        boolean mfaEnabled = !java.util.Set.of("SUPERADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT").contains(role)
                || platformUsers.findByUsernameIgnoreCase(principal.username()).map(PlatformUser::isMfaEnabled).orElse(false);
        return new AuthDtos.SessionResponse(true,
                new AuthDtos.UserInfo(principal.username(), role, principal.businessId(), slug, status, verified, mfaEnabled),
                principal.expiresAt());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = BearerTokenFilter.COOKIE_NAME, required = false) String cookie,
                                       HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getDetails() instanceof TokenService.TokenPrincipal principal)
            audit.record(principal.username(),principal.role(),principal.businessId(),"AUTH_LOGOUT","AUTHENTICATION",null,"SUCCESS","{}",request);
        sessions.revoke(cookie, "LOGOUT");
        SecurityContextHolder.clearContext();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return SessionCookieFactory.create(BearerTokenFilter.COOKIE_NAME, value, maxAge, cookieSecure, cookieSameSite, "");
    }
    private String normalizeIdentifier(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("[+0-9()\\s.-]+")) return normalizePhone(trimmed);
        return trimmed.toLowerCase();
    }
    private String normalizePhone(String value) { return value == null ? "" : value.trim().replaceAll("[^0-9+]", ""); }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable",ex); }
    }
    private String uniqueTenantTag(String base) {
        String candidate = base;
        int suffix = 2;
        while (businesses.existsByTenantTagIgnoreCase(candidate)) candidate = base + suffix++;
        return candidate;
    }
}
