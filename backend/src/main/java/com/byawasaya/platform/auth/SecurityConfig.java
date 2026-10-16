package com.byawasaya.platform.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.byawasaya.platform.api.ApiError;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.business.TenantTableNameResolver;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService, PlatformUserRepository platformUsers, PasswordMfaService passwordMfa, ObjectMapper mapper,
            BusinessRepository businesses, TenantTableNameResolver tables, JdbcTemplate jdbc, GoogleOAuthSuccessHandler oauthSuccessHandler,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.secure(cookieSecure).sameSite("Lax"));
        http.csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint(mapper)).accessDeniedHandler(accessDenied(mapper)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/plans", "/api/promotions/validate", "/api/auth/csrf", "/api/auth/login", "/api/auth/logout", "/api/auth/register-store", "/api/auth/email/verification/confirm", "/api/auth/password/forgot", "/api/auth/password/reset", "/api/tenant-applications/register", "/api/tenant-applications/draft", "/api/tenant-applications/draft/restore", "/api/store/**", "/api/storefront/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/mfa/verify-login").permitAll()
                .requestMatchers("/api/auth/mfa/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/customer/auth/register", "/api/customer/auth/resend-verification", "/api/customer/auth/login", "/api/customer/auth/mfa/verify-login", "/api/customer/auth/verify-email", "/api/customer/auth/password/forgot", "/api/customer/auth/password/reset").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/customer/auth/session").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/customer/auth/logout", "/api/customer/auth/password/change").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/customer/orders").hasRole("CUSTOMER")
                .requestMatchers("/api/auth/session", "/api/auth/password/change", "/api/auth/email/verification/resend").authenticated()
                .requestMatchers("/api/tenant-applications/me/**", "/api/tenant-applications/me").hasRole("STORE_OWNER")
                .requestMatchers("/api/platform/users/**", "/api/platform/plans/**", "/api/platform/subscriptions/**", "/api/platform/subscription-invoices/**", "/api/platform/promotions/**", "/api/platform/audit-logs/**").hasRole("SUPERADMIN")
                .requestMatchers("/api/tenant-billing/**").hasRole("STORE_OWNER")
                .requestMatchers("/api/platform/applications/**").hasAnyRole("SUPERADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT")
                .requestMatchers("/api/admin/**").hasAnyRole("SUPERADMIN", "PLATFORM_ADMIN")
                .requestMatchers("/api/platform/**").hasAnyRole("SUPERADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT")
                .requestMatchers(HttpMethod.GET, "/api/merchant/store", "/api/merchant/products", "/api/merchant/orders").hasAnyRole("STORE_OWNER", "STORE_STAFF")
                .requestMatchers(HttpMethod.PATCH, "/api/merchant/orders/*/status").hasAnyRole("STORE_OWNER", "STORE_STAFF")
                .requestMatchers("/api/merchant/staff", "/api/merchant/staff/**").hasRole("STORE_OWNER")
                .requestMatchers("/api/merchant/**", "/api/tenant/**").hasRole("STORE_OWNER")
                .anyRequest().denyAll())
            .oauth2Login(oauth -> oauth.successHandler(oauthSuccessHandler))
            .addFilterBefore(new BearerTokenFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new TenantStatusFilter(businesses, mapper), BearerTokenFilter.class)
            .addFilterAfter(new TenantStaffPermissionFilter(jdbc, tables, mapper), TenantStatusFilter.class)
            .addFilterAfter(new PlatformMfaEnforcementFilter(platformUsers, mapper), TenantStaffPermissionFilter.class);
        return http.build();
    }
    private AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), new ApiError(java.time.Instant.now(), 401, "Unauthorized", "A valid sign-in is required.", java.util.Map.of()));
        };
    }
    private AccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), new ApiError(java.time.Instant.now(), 403, "Forbidden", "You do not have permission to perform this action.", java.util.Map.of()));
        };
    }
}
