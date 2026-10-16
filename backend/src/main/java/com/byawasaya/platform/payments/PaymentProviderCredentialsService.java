package com.byawasaya.platform.payments;

import com.byawasaya.platform.business.Business;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.business.TenantTableNameResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Stores each tenant's gateway secret in a versioned AES-GCM envelope. */
@Service
public class PaymentProviderCredentialsService {
    private static final String TABLE = "tenant_payment_credentials";
    private final JdbcTemplate jdbc;
    private final BusinessRepository businesses;
    private final TenantTableNameResolver tables;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private final String encryptionKeyBase64;

    public PaymentProviderCredentialsService(
            JdbcTemplate jdbc,
            BusinessRepository businesses,
            TenantTableNameResolver tables,
            ObjectMapper mapper,
            @Value("${app.payment.encryption-key-base64:}") String encryptionKeyBase64) {
        this.jdbc = jdbc;
        this.businesses = businesses;
        this.tables = tables;
        this.mapper = mapper;
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    @Transactional(readOnly = true)
    public PaymentProviderDtos.Settings settings(UUID businessId) {
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        return new PaymentProviderDtos.Settings(state(businessId, PaymentProvider.ESEWA),
                state(businessId, PaymentProvider.KHALTI), business.isOnlinePaymentEnabled());
    }

    @Transactional
    public PaymentProviderDtos.Settings save(UUID businessId, PaymentProvider provider,
            PaymentProviderDtos.ConnectionRequest request) {
        ProviderConfiguration existing = configuration(businessId, provider).orElse(null);
        String secret = request.secretKey() == null || request.secretKey().isBlank()
                ? existing == null ? null : existing.secretKey()
                : request.secretKey().trim();
        String merchantCode = request.merchantCode() == null || request.merchantCode().isBlank()
                ? existing == null ? null : existing.merchantCode()
                : request.merchantCode().trim();

        if (request.enabled() && (secret == null || secret.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the provider secret before enabling this connection.");
        if (provider == PaymentProvider.ESEWA && (merchantCode == null || merchantCode.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the eSewa merchant product code.");
        if (secret != null && secret.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider secret is too long.");

        String encrypted = secret == null ? null
                : encrypt(new ProviderConfiguration(merchantCode, secret, request.sandbox()));
        if (encrypted == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add provider credentials before saving this connection.");

        jdbc.update("""
                INSERT INTO tenant_payment_credentials(id,business_id,provider,encrypted_configuration,sandbox,enabled)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT (business_id,provider) DO UPDATE SET
                    encrypted_configuration=EXCLUDED.encrypted_configuration,
                    sandbox=EXCLUDED.sandbox, enabled=EXCLUDED.enabled, updated_at=now()
                """, UUID.randomUUID(), businessId, provider.name(), encrypted, request.sandbox(), request.enabled());
        if (!anyEnabled(businessId)) disableOnlinePayment(businessId);
        return settings(businessId);
    }

    @Transactional(readOnly = true)
    public boolean anyEnabled(UUID businessId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + TABLE + " WHERE business_id=? AND enabled=true", Integer.class, businessId);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID businessId, PaymentProvider provider) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + TABLE + " WHERE business_id=? AND provider=? AND enabled=true", Integer.class, businessId, provider.name());
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentProvider> enabledProviders(UUID businessId) {
        return jdbc.query("SELECT provider FROM " + TABLE + " WHERE business_id=? AND enabled=true ORDER BY provider",
                (rs, row) -> PaymentProvider.valueOf(rs.getString(1)), businessId);
    }

    @Transactional(readOnly = true)
    public ProviderConfiguration require(UUID businessId, PaymentProvider provider) {
        return configuration(businessId, provider)
                .filter(ProviderConfiguration::enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "This store has not connected that payment provider."));
    }

    @Transactional(readOnly = true)
    public String encryptSnapshot(ProviderConfiguration configuration) { return encrypt(configuration); }

    public ProviderConfiguration decryptSnapshot(String encrypted) { return decrypt(encrypted); }

    private PaymentProviderDtos.ProviderState state(UUID businessId, PaymentProvider provider) {
        return jdbc.query("SELECT sandbox,enabled FROM " + TABLE + " WHERE business_id=? AND provider=?",
                rs -> rs.next() ? new PaymentProviderDtos.ProviderState(true, rs.getBoolean("enabled"), rs.getBoolean("sandbox"))
                        : new PaymentProviderDtos.ProviderState(false, false, false), businessId, provider.name());
    }

    private java.util.Optional<ProviderConfiguration> configuration(UUID businessId, PaymentProvider provider) {
        var rows = jdbc.query("SELECT encrypted_configuration,sandbox,enabled FROM " + TABLE + " WHERE business_id=? AND provider=?",
                (rs, row) -> new StoredConfiguration(rs.getString("encrypted_configuration"), rs.getBoolean("sandbox"), rs.getBoolean("enabled")),
                businessId, provider.name());
        if (rows.isEmpty()) return java.util.Optional.empty();
        ProviderConfiguration decrypted = decrypt(rows.getFirst().encryptedConfiguration());
        return java.util.Optional.of(new ProviderConfiguration(decrypted.merchantCode(), decrypted.secretKey(), rows.getFirst().sandbox(), rows.getFirst().enabled()));
    }

    private void disableOnlinePayment(UUID businessId) {
        jdbc.update("UPDATE businesses SET online_payment_enabled=false WHERE id=?", businessId);
        tables.prefix(businessId).ifPresent(prefix -> jdbc.update("UPDATE " + tables.table(prefix, "store_settings")
                + " SET online_payment_enabled=false WHERE id=1"));
    }

    private String encrypt(ProviderConfiguration config) {
        byte[] key = encryptionKey();
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(Map.of(
                    "merchantCode", config.merchantCode() == null ? "" : config.merchantCode(),
                    "secretKey", config.secretKey(),
                    "sandbox", config.sandbox())));
            byte[] envelope = new byte[4 + iv.length + encrypted.length];
            envelope[0] = 'P'; envelope[1] = 'A'; envelope[2] = 'Y'; envelope[3] = '1';
            System.arraycopy(iv, 0, envelope, 4, iv.length);
            System.arraycopy(encrypted, 0, envelope, 16, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (Exception error) {
            throw new IllegalStateException("Could not encrypt payment provider credentials.", error);
        }
    }

    private ProviderConfiguration decrypt(String encoded) {
        byte[] envelope = Base64.getDecoder().decode(encoded);
        if (envelope.length < 32 || envelope[0] != 'P' || envelope[1] != 'A' || envelope[2] != 'Y' || envelope[3] != '1')
            throw new IllegalStateException("Stored payment credentials have an invalid encryption envelope.");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, envelope, 4, 12));
            Map<String, Object> config = mapper.readValue(cipher.doFinal(envelope, 16, envelope.length - 16),
                    new TypeReference<>() {});
            return new ProviderConfiguration((String) config.get("merchantCode"), (String) config.get("secretKey"),
                    Boolean.TRUE.equals(config.get("sandbox")), false);
        } catch (Exception error) {
            throw new IllegalStateException("Could not decrypt payment provider credentials.", error);
        }
    }

    private byte[] encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKeyBase64);
            if (key.length != 32) throw new IllegalStateException("APP_PAYMENT_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
            return key;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("APP_PAYMENT_ENCRYPTION_KEY_BASE64 must be valid Base64 for a 32-byte key.", invalid);
        }
    }

    private record StoredConfiguration(String encryptedConfiguration, boolean sandbox, boolean enabled) {}
    public record ProviderConfiguration(String merchantCode, String secretKey, boolean sandbox, boolean enabled) {
        public ProviderConfiguration(String merchantCode, String secretKey, boolean sandbox) { this(merchantCode, secretKey, sandbox, false); }
    }
}
