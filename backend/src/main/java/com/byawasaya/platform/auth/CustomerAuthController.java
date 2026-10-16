package com.byawasaya.platform.auth;

import com.byawasaya.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/auth")
public class CustomerAuthController {
    private static final String GENERIC_REGISTRATION_MESSAGE = "If this email can be registered, a verification link has been sent.";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TokenService sessions;
    private final LoginRateLimitService rateLimit;
    private final PasswordMfaService passwordMfa;
    private final JavaMailSender mailSender;
    private final AuditService audit;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookieDomain;
    private final String frontendUrl;
    private final String mailFrom;
    private final SecureRandom random = new SecureRandom();

    public CustomerAuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TokenService sessions,
            LoginRateLimitService rateLimit, PasswordMfaService passwordMfa, JavaMailSender mailSender, AuditService audit,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.auth.cookie-same-site:Lax}") String cookieSameSite,
            @Value("${app.auth.customer-cookie-domain:}") String cookieDomain,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.mail.from:no-reply@byawasaya.local}") String mailFrom) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.rateLimit = rateLimit;
        this.passwordMfa = passwordMfa;
        this.mailSender = mailSender;
        this.audit = audit;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.mailFrom = mailFrom;
    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<CustomerAuthDtos.AuthResponse> register(@Valid @RequestBody CustomerAuthDtos.RegisterRequest request,
            HttpServletRequest servletRequest) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String ip = servletRequest.getRemoteAddr();
        rateLimit.check(email, ip);
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM customer_accounts WHERE lower(email)=?", Integer.class, email);
        if (exists != null && exists > 0)
            return ResponseEntity.accepted().body(new CustomerAuthDtos.AuthResponse(GENERIC_REGISTRATION_MESSAGE));

        UUID accountId = UUID.randomUUID();
        String rawToken = randomToken();
        int inserted = jdbc.update("INSERT INTO customer_accounts(id,email,password_hash,full_name,phone) VALUES(?,?,?,?,?) ON CONFLICT (lower(email)) DO NOTHING",
                accountId, email, passwordEncoder.encode(request.password()), request.fullName().trim(), blank(request.phone()));
        if (inserted == 0)
            return ResponseEntity.accepted().body(new CustomerAuthDtos.AuthResponse(GENERIC_REGISTRATION_MESSAGE));
        jdbc.update("INSERT INTO customer_email_verification_tokens(id,customer_account_id,token_hash,expires_at) VALUES(?,?,?,?)",
                UUID.randomUUID(), accountId, sha256(rawToken), Instant.now().plus(Duration.ofHours(24)));

        boolean emailSent = sendVerificationEmail(email, rawToken);
        audit.record(email, "CUSTOMER", null, "CUSTOMER_REGISTERED", "CUSTOMER_ACCOUNT", accountId.toString(),
                emailSent ? "SUCCESS" : "FAILED", "{}", servletRequest);
        return ResponseEntity.accepted().body(new CustomerAuthDtos.AuthResponse(GENERIC_REGISTRATION_MESSAGE));
    }

    @Transactional
    @PostMapping("/resend-verification")
    public ResponseEntity<CustomerAuthDtos.AuthResponse> resendVerification(
            @Valid @RequestBody CustomerAuthDtos.EmailRequest request, HttpServletRequest servletRequest) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String ip = servletRequest.getRemoteAddr();
        rateLimit.check(email, ip);
        var accounts = jdbc.query("SELECT id,email FROM customer_accounts WHERE lower(email)=? AND enabled=true AND email_verified=false FOR UPDATE",
                (rs, row) -> new AccountEmail(rs.getObject("id", UUID.class), rs.getString("email")), email);
        if (!accounts.isEmpty()) {
            AccountEmail account = accounts.getFirst();
            String rawToken = randomToken();
            jdbc.update("UPDATE customer_email_verification_tokens SET consumed_at=COALESCE(consumed_at,now()) WHERE customer_account_id=? AND consumed_at IS NULL",
                    account.id());
            jdbc.update("INSERT INTO customer_email_verification_tokens(id,customer_account_id,token_hash,expires_at) VALUES(?,?,?,?)",
                    UUID.randomUUID(), account.id(), sha256(rawToken), Instant.now().plus(Duration.ofHours(24)));
            boolean emailSent = sendVerificationEmail(account.email(), rawToken);
            audit.record(account.email(), "CUSTOMER", null, "CUSTOMER_VERIFICATION_RESENT", "CUSTOMER_ACCOUNT",
                    account.id().toString(), emailSent ? "SUCCESS" : "FAILED", "{}", servletRequest);
        }
        return ResponseEntity.accepted().body(new CustomerAuthDtos.AuthResponse(GENERIC_REGISTRATION_MESSAGE));
    }

    @Transactional
    @PostMapping("/password/forgot")
    public CustomerAuthDtos.AuthResponse forgotPassword(@Valid @RequestBody CustomerAuthDtos.EmailRequest request,
            HttpServletRequest servletRequest) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String ip = servletRequest.getRemoteAddr();
        String rateKey = "customer-password-reset:" + email;
        rateLimit.check(rateKey, ip);
        rateLimit.failure(rateKey, ip);
        var accounts = jdbc.query("SELECT id,email FROM customer_accounts WHERE lower(email)=? AND enabled=true AND email_verified=true FOR UPDATE",
                (rs, row) -> new AccountEmail(rs.getObject("id", UUID.class), rs.getString("email")), email);
        if (!accounts.isEmpty()) {
            AccountEmail account = accounts.getFirst();
            String rawToken = randomToken();
            jdbc.update("UPDATE customer_password_reset_tokens SET consumed_at=COALESCE(consumed_at,now()) WHERE customer_account_id=? AND consumed_at IS NULL",
                    account.id());
            jdbc.update("INSERT INTO customer_password_reset_tokens(id,customer_account_id,token_hash,expires_at) VALUES(?,?,?,?)",
                    UUID.randomUUID(), account.id(), sha256(rawToken), Instant.now().plus(Duration.ofMinutes(30)));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(account.email());
            message.setSubject("Reset your Byawasaya customer password");
            message.setText("Reset your password within 30 minutes: " + frontendUrl + "/customer/reset-password?token="
                    + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
            boolean emailSent;
            try { mailSender.send(message); emailSent = true; }
            catch (RuntimeException mailFailure) { emailSent = false; }
            audit.record(account.email(), "CUSTOMER", null, "CUSTOMER_PASSWORD_RESET_REQUESTED", "CUSTOMER_ACCOUNT",
                    account.id().toString(), emailSent ? "SUCCESS" : "FAILED", "{}", servletRequest);
        }
        return new CustomerAuthDtos.AuthResponse("If a verified account matches that email, password-reset instructions will be sent.");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @PostMapping("/password/reset")
    public CustomerAuthDtos.AuthResponse resetPassword(@Valid @RequestBody CustomerAuthDtos.ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        rateLimit.check("customer-password-reset-confirm", ip);
        UUID accountId = jdbc.query("""
                UPDATE customer_password_reset_tokens
                   SET consumed_at=now()
                 WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now()
                RETURNING customer_account_id
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sha256(request.token()));
        if (accountId == null) {
            rateLimit.failure("customer-password-reset-confirm", ip);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This password-reset link is invalid or expired.");
        }
        var accounts = jdbc.query("UPDATE customer_accounts SET password_hash=?,updated_at=now() WHERE id=? AND enabled=true RETURNING email",
                (rs, row) -> rs.getString(1), passwordEncoder.encode(request.password()), accountId);
        if (accounts.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This customer account is unavailable.");
        String email = accounts.getFirst();
        sessions.revokeAccountKey("customer:" + accountId, "PASSWORD_RESET");
        rateLimit.success("customer-password-reset-confirm", ip);
        audit.record(email, "CUSTOMER", null, "CUSTOMER_PASSWORD_RESET", "CUSTOMER_ACCOUNT", accountId.toString(),
                "SUCCESS", "{}", servletRequest);
        return new CustomerAuthDtos.AuthResponse("Password changed. Sign in again with your new password.");
    }

    @Transactional
    @PostMapping("/verify-email")
    public CustomerAuthDtos.AuthResponse verifyEmail(@Valid @RequestBody CustomerAuthDtos.VerifyEmailRequest request,
            HttpServletRequest servletRequest) {
        UUID accountId = jdbc.query("""
                UPDATE customer_email_verification_tokens
                   SET consumed_at=now()
                 WHERE token_hash=? AND consumed_at IS NULL AND expires_at>now()
                RETURNING customer_account_id
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, sha256(request.token()));
        if (accountId == null) {
            Boolean alreadyVerified = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM customer_email_verification_tokens tokens
                        JOIN customer_accounts accounts ON accounts.id=tokens.customer_account_id
                       WHERE tokens.token_hash=? AND tokens.consumed_at IS NOT NULL AND accounts.email_verified=true)
                    """, Boolean.class, sha256(request.token()));
            if (Boolean.TRUE.equals(alreadyVerified))
                return new CustomerAuthDtos.AuthResponse("Email verified. You can now sign in and view your orders.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link is invalid or expired. Register again to receive a new link.");
        }
        jdbc.update("UPDATE customer_accounts SET email_verified=true,updated_at=now() WHERE id=?", accountId);
        String email = jdbc.queryForObject("SELECT email FROM customer_accounts WHERE id=?", String.class, accountId);
        audit.record(email, "CUSTOMER", null, "CUSTOMER_EMAIL_VERIFIED", "CUSTOMER_ACCOUNT", accountId.toString(), "SUCCESS", "{}", servletRequest);
        return new CustomerAuthDtos.AuthResponse("Email verified. You can now sign in and view your orders.");
    }

    @PostMapping("/login")
    public CustomerAuthDtos.LoginResponse login(@Valid @RequestBody CustomerAuthDtos.LoginRequest request,
            HttpServletResponse response, HttpServletRequest servletRequest) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String ip = servletRequest.getRemoteAddr();
        rateLimit.check(email, ip);
        var rows = jdbc.query("SELECT id,email,password_hash,full_name,phone,email_verified,enabled FROM customer_accounts WHERE lower(email)=?",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                        rs.getString("full_name"), rs.getString("phone"), rs.getBoolean("email_verified"), rs.getBoolean("enabled")), email);
        if (rows.isEmpty() || !rows.getFirst().enabled() || !passwordEncoder.matches(request.password(), rows.getFirst().passwordHash())) {
            rateLimit.failure(email, ip);
            audit.record(null, "CUSTOMER", null, "CUSTOMER_LOGIN_FAILED", "AUTHENTICATION", null, "FAILED",
                    "{\"identifierHash\":\"" + sha256(email) + "\"}", servletRequest);
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid email or password.");
        }
        Account account = rows.getFirst();
        if (!account.emailVerified()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before signing in.");
        rateLimit.success(email, ip);
        String challenge = passwordMfa.issue("customer:" + account.id(), account.email(), "CUSTOMER", null,
                account.phone(), ip);
        return new CustomerAuthDtos.LoginResponse(300, null, true, challenge);
    }

    @PostMapping("/mfa/verify-login")
    public ResponseEntity<CustomerAuthDtos.LoginResponse> verifyLogin(@Valid @RequestBody AuthDtos.MfaLoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        TokenService.IssuedSession issued = passwordMfa.verify(request.challengeToken(), request.code(), servletRequest);
        if (!"CUSTOMER".equals(issued.principal().role()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The sign-in challenge is invalid.");
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.value(), Duration.ofSeconds(issued.expiresIn())).toString());
        TokenService.TokenPrincipal principal = issued.principal();
        UUID id = accountId(principal);
        var account = jdbc.queryForObject("SELECT email,full_name,email_verified FROM customer_accounts WHERE id=?",
                (rs, row) -> new CustomerAuthDtos.CustomerInfo(id, rs.getString("email"), rs.getString("full_name"),
                        rs.getBoolean("email_verified")), id);
        audit.record(principal.username(), "CUSTOMER", null, "CUSTOMER_LOGIN_SUCCEEDED", "AUTHENTICATION", null, "SUCCESS", "{}", servletRequest);
        return ResponseEntity.ok(new CustomerAuthDtos.LoginResponse(issued.expiresIn(), account, false, null));
    }

    @GetMapping("/session")
    public CustomerAuthDtos.SessionResponse session(Authentication authentication) {
        TokenService.TokenPrincipal principal = requireCustomer(authentication);
        UUID id = accountId(principal);
        var rows = jdbc.query("SELECT id,email,full_name,email_verified,enabled FROM customer_accounts WHERE id=?",
                (rs, row) -> new AccountInfo(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"), rs.getBoolean("email_verified"), rs.getBoolean("enabled")), id);
        if (rows.isEmpty() || !rows.getFirst().enabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Customer account is unavailable.");
        var account = rows.getFirst();
        return new CustomerAuthDtos.SessionResponse(true, new CustomerAuthDtos.CustomerInfo(account.id(), account.email(), account.fullName(), account.emailVerified()), principal.expiresAt());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = BearerTokenFilter.CUSTOMER_COOKIE_NAME, required = false) String cookie,
            Authentication authentication, HttpServletResponse response, HttpServletRequest request) {
        TokenService.TokenPrincipal principal = requireCustomer(authentication);
        audit.record(principal.username(), "CUSTOMER", null, "CUSTOMER_LOGOUT", "AUTHENTICATION", null, "SUCCESS", "{}", request);
        sessions.revoke(cookie, "LOGOUT");
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    public static UUID accountId(TokenService.TokenPrincipal principal) {
        if (principal == null || !"CUSTOMER".equals(principal.role()) || principal.accountKey() == null || !principal.accountKey().startsWith("customer:"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A signed-in customer account is required.");
        try { return UUID.fromString(principal.accountKey().substring("customer:".length())); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid customer session."); }
    }

    public static TokenService.TokenPrincipal requireCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof TokenService.TokenPrincipal principal))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid customer sign-in is required.");
        accountId(principal);
        return principal;
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return SessionCookieFactory.create(BearerTokenFilter.CUSTOMER_COOKIE_NAME, value, maxAge, cookieSecure, cookieSameSite, cookieDomain);
    }
    private boolean sendVerificationEmail(String email, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Verify your Byawasaya customer account");
        message.setText("Verify your email within 24 hours: " + frontendUrl + "/customer/verify?token="
                + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        try { mailSender.send(message); return true; }
        catch (RuntimeException mailFailure) { return false; }
    }
    private String randomToken() { byte[] token = new byte[32]; random.nextBytes(token); return Base64.getUrlEncoder().withoutPadding().encodeToString(token); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 is unavailable.", error); }
    }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record Account(UUID id, String email, String passwordHash, String fullName, String phone, boolean emailVerified, boolean enabled) {}
    private record AccountEmail(UUID id, String email) {}
    private record AccountInfo(UUID id, String email, String fullName, boolean emailVerified, boolean enabled) {}
}
