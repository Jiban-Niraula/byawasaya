package com.byawasaya.platform.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RFC 6238 TOTP and authenticated encryption for platform MFA seeds. */
@Component
public class PlatformMfaCrypto {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int TOTP_PERIOD_SECONDS = 30;
    private static final int TOTP_DIGITS = 6;
    private static final int ALLOWED_CLOCK_DRIFT_STEPS = 1;
    private static final String AAD_PREFIX = "byawasaya-platform-mfa:v1:";
    private final String encryptionKeyBase64;

    public PlatformMfaCrypto(@Value("${app.auth.mfa-encryption-key-base64:}") String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    public String encrypt(byte[] secret, UUID userId) {
        try {
            byte[] nonce = new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((AAD_PREFIX + userId).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(secret);
            byte[] result = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt the platform MFA secret.", ex);
        }
    }

    public byte[] decrypt(String encryptedSecret, UUID userId) {
        if (encryptedSecret == null || !encryptedSecret.startsWith("v1."))
            throw new IllegalStateException("The stored platform MFA secret has an unsupported format.");
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encryptedSecret.substring(3));
            if (packed.length < 12 + 16) throw new IllegalStateException("The stored platform MFA secret is incomplete.");
            byte[] nonce = Arrays.copyOfRange(packed, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((AAD_PREFIX + userId).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt the platform MFA secret. Check the configured key.", ex);
        }
    }

    public String provisioningUri(String username, byte[] secret) {
        String label = URLEncoder.encode("Byawasaya:" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + encodeBase32(secret)
                + "&issuer=Byawasaya&algorithm=SHA1&digits=" + TOTP_DIGITS + "&period=" + TOTP_PERIOD_SECONDS;
    }

    public String encodeBase32(byte[] input) {
        StringBuilder result = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(BASE32[(buffer >>> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }

    public byte[] decodeBase32(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "").replace("-", "");
        ByteBuffer output = ByteBuffer.allocate(normalized.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int digit = base32Digit(normalized.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32 value.");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                output.put((byte) ((buffer >>> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return Arrays.copyOf(output.array(), output.position());
    }

    public String codeForStep(byte[] secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable.", ex);
        }
    }

    /** Returns the matching time step if the code is valid and has not been used before. */
    public Long matchingStep(byte[] secret, String submittedCode, long epochSeconds, Long lastUsedStep) {
        if (submittedCode == null || !submittedCode.matches("[0-9]{6}")) return null;
        long currentStep = epochSeconds / TOTP_PERIOD_SECONDS;
        for (int offset : new int[] { 0, -1, 1 }) {
            long step = currentStep + offset;
            if (step < 0 || (lastUsedStep != null && step <= lastUsedStep)) continue;
            if (MessageDigest.isEqual(codeForStep(secret, step).getBytes(StandardCharsets.US_ASCII),
                    submittedCode.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return null;
    }

    private byte[] encryptionKey() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank())
            throw new IllegalStateException("APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64 must be configured before MFA enrollment.");
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encryptionKeyBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64 must be valid Base64.", ex);
        }
        if (key.length != 32) throw new IllegalStateException("APP_PLATFORM_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes.");
        return key;
    }

    private static int base32Digit(char value) {
        for (int i = 0; i < BASE32.length; i++) if (BASE32[i] == value) return i;
        return -1;
    }
}
