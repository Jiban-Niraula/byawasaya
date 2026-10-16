package com.byawasaya.platform.config;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fail fast instead of starting a production instance with development credentials or insecure cookies. */
@Component
@Profile("production")
public class ProductionConfigurationValidator implements ApplicationRunner {
    private final String dbPassword;
    private final String frontendUrl;
    private final String backendPublicUrl;
    private final String storageEndpoint;
    private final String storageAccessKey;
    private final String storageSecretKey;
    private final String storageEncryptionKey;
    private final String paymentEncryptionKey;
    private final String platformMfaEncryptionKey;
    private final String mailFrom;
    private final String dbUrl;
    private final String dbUsername;
    private final String redisPassword;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;
    private final String storageBucket;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String corsOrigins;
    private final String baseDomain;
    private final String customerCookieDomain;
    private final String malwareScannerHost;
    private final int malwareScannerPort;
    private final boolean bootstrapEnabled;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.backend-public-url}") String backendPublicUrl,
            @Value("${app.storage.endpoint}") String storageEndpoint,
            @Value("${app.storage.access-key}") String storageAccessKey,
            @Value("${app.storage.secret-key}") String storageSecretKey,
            @Value("${app.storage.encryption-key-base64}") String storageEncryptionKey,
            @Value("${app.payment.encryption-key-base64}") String paymentEncryptionKey,
            @Value("${app.auth.mfa-encryption-key-base64}") String platformMfaEncryptionKey,
            @Value("${app.mail.from}") String mailFrom,
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${spring.mail.host}") String mailHost,
            @Value("${spring.mail.username}") String mailUsername,
            @Value("${spring.mail.password}") String mailPassword,
            @Value("${app.storage.bucket}") String storageBucket,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String googleClientSecret,
            @Value("${app.cors.allowed-origins}") String corsOrigins,
            @Value("${app.tenant.base-domain}") String baseDomain,
            @Value("${app.auth.customer-cookie-domain}") String customerCookieDomain,
            @Value("${app.security.malware-scanner.host}") String malwareScannerHost,
            @Value("${app.security.malware-scanner.port}") int malwareScannerPort,
            @Value("${app.auth.superadmin.bootstrap-enabled:false}") boolean bootstrapEnabled,
            @Value("${app.auth.superadmin.username}") String bootstrapUsername,
            @Value("${app.auth.superadmin.password}") String bootstrapPassword) {
        this.dbPassword = dbPassword;
        this.frontendUrl = frontendUrl;
        this.backendPublicUrl = backendPublicUrl;
        this.storageEndpoint = storageEndpoint;
        this.storageAccessKey = storageAccessKey;
        this.storageSecretKey = storageSecretKey;
        this.storageEncryptionKey = storageEncryptionKey;
        this.paymentEncryptionKey = paymentEncryptionKey;
        this.platformMfaEncryptionKey = platformMfaEncryptionKey;
        this.mailFrom = mailFrom;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.redisPassword = redisPassword;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.storageBucket = storageBucket;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.corsOrigins = corsOrigins;
        this.baseDomain = baseDomain;
        this.customerCookieDomain = customerCookieDomain;
        this.malwareScannerHost = malwareScannerHost;
        this.malwareScannerPort = malwareScannerPort;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireNotDefault("DB_PASSWORD", dbPassword, "byawasaya_dev");
        requireNotDefault("S3_ACCESS_KEY", storageAccessKey, "byawasaya-local");
        requireNotDefault("S3_SECRET_KEY", storageSecretKey, "replace-this-local-minio-secret");
        requireValue("DB_URL", dbUrl);
        requireValue("DB_USERNAME", dbUsername);
        requireValue("REDIS_PASSWORD", redisPassword);
        requireValue("MAIL_HOST", mailHost);
        requireValue("MAIL_USERNAME", mailUsername);
        requireValue("MAIL_PASSWORD", mailPassword);
        requireValue("DOMAIN_NAME", baseDomain);
        requireNotDefault("MALWARE_SCANNER_HOST", malwareScannerHost, "localhost");
        if (malwareScannerPort < 1 || malwareScannerPort > 65535)
            throw new IllegalStateException("MALWARE_SCANNER_PORT must be between 1 and 65535.");
        requireValue("S3_BUCKET", storageBucket);
        requireNotDefault("GOOGLE_CLIENT_ID", googleClientId, "local-placeholder-client-id");
        requireNotDefault("GOOGLE_CLIENT_SECRET", googleClientSecret, "local-placeholder-client-secret");
        if (bootstrapEnabled && (bootstrapUsername == null || bootstrapUsername.isBlank() || bootstrapPassword == null || bootstrapPassword.length() < 16))
            throw new IllegalStateException("One-time super-admin bootstrap requires a username and a password of at least 16 characters.");
        if (bootstrapEnabled) requireNotDefault("SUPERADMIN_PASSWORD", bootstrapPassword, "");
        requireHttps("FRONTEND_URL", frontendUrl);
        requireHttps("BACKEND_PUBLIC_URL", backendPublicUrl);
        requireHttps("S3_ENDPOINT", storageEndpoint);
        if (mailFrom == null || !mailFrom.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalStateException("MAIL_FROM must be a valid production email address.");
        if (corsOrigins == null || corsOrigins.isBlank())
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain the HTTPS application origin.");
        for (String origin : corsOrigins.split(",")) requireHttps("CORS_ALLOWED_ORIGINS", origin.trim());
        if (!baseDomain.matches("(?i)(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}"))
            throw new IllegalStateException("DOMAIN_NAME must be a public DNS hostname without a scheme or path.");
        if (!("." + baseDomain).equalsIgnoreCase(customerCookieDomain))
            throw new IllegalStateException("The production customer session cookie domain must be '.DOMAIN_NAME' so customer sign-in works across tenant subdomains.");
        try {
            if (Base64.getDecoder().decode(storageEncryptionKey).length != 32)
                throw new IllegalStateException("APP_STORAGE_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
            if (Base64.getDecoder().decode(paymentEncryptionKey).length != 32)
                throw new IllegalStateException("APP_PAYMENT_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
            if (Base64.getDecoder().decode(platformMfaEncryptionKey).length != 32)
                throw new IllegalStateException("APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
        } catch (IllegalArgumentException invalidKey) {
            throw new IllegalStateException("Storage, payment and platform MFA encryption keys must be valid Base64 for 32-byte keys.", invalidKey);
        }
    }

    private static void requireHttps(String key, String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalStateException(key + " must use an HTTPS URL with a hostname in production.");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(key + " must be a valid HTTPS URL in production.", invalid);
        }
    }

    private static void requireNotDefault(String key, String value, String developmentValue) {
        if (value == null || value.isBlank() || value.equals(developmentValue)
                || value.toLowerCase(Locale.ROOT).startsWith("replace_"))
            throw new IllegalStateException(key + " must be replaced with a production secret.");
    }

    private static void requireValue(String key, String value) {
        if (value == null || value.isBlank() || value.startsWith("?"))
            throw new IllegalStateException(key + " must be configured in production.");
    }
}
