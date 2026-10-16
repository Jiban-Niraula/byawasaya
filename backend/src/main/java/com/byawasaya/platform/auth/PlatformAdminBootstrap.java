package com.byawasaya.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.superadmin.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class PlatformAdminBootstrap implements CommandLineRunner {
    private final PlatformUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String phone;

    public PlatformAdminBootstrap(PlatformUserRepository users, PasswordEncoder encoder,
            @Value("${app.auth.superadmin.username}") String username,
            @Value("${app.auth.superadmin.password}") String password,
            @Value("${app.auth.superadmin.phone:}") String phone) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.phone = phone;
    }

    @Override
    public void run(String... args) {
        if (password.isBlank()) return;
        if (!users.existsByUsernameIgnoreCase(username)) {
            if (phone.isBlank()) throw new IllegalStateException("SUPERADMIN_PHONE is required.");
            if (password.length() < 12) throw new IllegalStateException("SUPERADMIN_PASSWORD must be at least 12 characters.");
            users.save(new PlatformUser(username, encoder.encode(password),
                    "Byawasaya Platform Administrator", "SUPERADMIN", phone));
        }
    }
}
