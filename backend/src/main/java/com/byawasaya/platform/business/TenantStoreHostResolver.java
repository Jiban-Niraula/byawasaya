package com.byawasaya.platform.business;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves one-level Byawasaya tenant subdomains; request hosts never become SQL identifiers. */
@Component
public class TenantStoreHostResolver {
    private static final Set<String> RESERVED = Set.of("www", "app", "admin", "api", "mail", "status", "support");
    private final String baseDomain;

    public TenantStoreHostResolver(@Value("${app.tenant.base-domain:}") String baseDomain) {
        this.baseDomain = baseDomain == null ? "" : baseDomain.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$", "");
    }

    public Optional<String> tenantSlug(String host) {
        if (baseDomain.isBlank() || host == null) return Optional.empty();
        String normalized = host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        String suffix = "." + baseDomain;
        if (!normalized.endsWith(suffix)) return Optional.empty();
        String label = normalized.substring(0, normalized.length() - suffix.length());
        if (label.contains(".") || RESERVED.contains(label) || !label.matches("[a-z0-9][a-z0-9-]{1,58}[a-z0-9]"))
            return Optional.empty();
        return Optional.of(label);
    }
}
