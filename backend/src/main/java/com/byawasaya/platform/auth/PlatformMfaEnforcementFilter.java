package com.byawasaya.platform.auth;

import com.byawasaya.platform.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects platform sessions as soon as their account is disabled. Login issues platform sessions only after MFA verification. */
public class PlatformMfaEnforcementFilter extends OncePerRequestFilter {
    private static final Set<String> PLATFORM_ROLES = Set.of("SUPERADMIN", "PLATFORM_ADMIN", "PLATFORM_SUPPORT");
    private final PlatformUserRepository users;
    private final ObjectMapper mapper;

    public PlatformMfaEnforcementFilter(PlatformUserRepository users, ObjectMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof TokenService.TokenPrincipal principal)
                || !PLATFORM_ROLES.contains(principal.role())) {
            chain.doFilter(request, response);
            return;
        }

        PlatformUser user = users.findByUsernameIgnoreCase(principal.username()).orElse(null);
        if (user == null || !user.isEnabled()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 401, "Unauthorized",
                    "The platform account is unavailable. Sign in again.", Map.of()));
            return;
        }

        chain.doFilter(request, response);
    }
}
