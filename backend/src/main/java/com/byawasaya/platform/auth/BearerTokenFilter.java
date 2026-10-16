package com.byawasaya.platform.auth;

import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Reads only the HttpOnly session cookie; legacy bearer tokens are intentionally not accepted. */
public class BearerTokenFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "BYAWASAYA_SESSION";
    public static final String CUSTOMER_COOKIE_NAME = "BYAWASAYA_CUSTOMER_SESSION";
    private final TokenService tokenService;
    public BearerTokenFilter(TokenService tokenService) { this.tokenService = tokenService; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        Cookie[] cookies = request.getCookies();
        String cookieName = customerSessionRoute(request) ? CUSTOMER_COOKIE_NAME : COOKIE_NAME;
        if (cookies != null) for (Cookie cookie : cookies) if (cookieName.equals(cookie.getName())) { token = cookie.getValue(); break; }
        var principal = tokenService.principalFor(token);
        if (principal != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var auth = UsernamePasswordAuthenticationToken.authenticated(principal.username(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
            auth.setDetails(principal);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private boolean customerSessionRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/customer/")) return true;
        return "POST".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/store/") && path.endsWith("/orders");
    }
}
