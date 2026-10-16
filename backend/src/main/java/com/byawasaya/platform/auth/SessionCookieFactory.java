package com.byawasaya.platform.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class SessionCookieFactory {
    private SessionCookieFactory() {}

    public static ResponseCookie create(String name, String value, Duration maxAge, boolean secure, String sameSite, String domain) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite(sameSite).path("/").maxAge(maxAge);
        if (domain != null && !domain.isBlank()) builder.domain(domain.trim());
        return builder.build();
    }
}
