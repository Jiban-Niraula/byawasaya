package com.byawasaya.platform.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformMfaCryptoTest {
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final PlatformMfaCrypto crypto = new PlatformMfaCrypto(
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)));

    @Test
    void base32RoundTripsAuthenticatorSecret() {
        String encoded = crypto.encodeBase32(RFC_SECRET);

        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", encoded);
        assertArrayEquals(RFC_SECRET, crypto.decodeBase32(encoded));
    }

    @Test
    void hotpMatchesRfc4226Vectors() {
        assertEquals("755224", crypto.codeForStep(RFC_SECRET, 0));
        assertEquals("287082", crypto.codeForStep(RFC_SECRET, 1));
        assertEquals("359152", crypto.codeForStep(RFC_SECRET, 2));
    }

    @Test
    void totpAcceptsClockDriftButRejectsAReplayedStep() {
        String code = crypto.codeForStep(RFC_SECRET, 1);

        assertEquals(1L, crypto.matchingStep(RFC_SECRET, code, 60, null));
        assertNull(crypto.matchingStep(RFC_SECRET, code, 60, 1L));
        assertNull(crypto.matchingStep(RFC_SECRET, "12x456", 60, null));
    }

    @Test
    void mfaSeedIsAuthenticatedAndBoundToItsPlatformAccount() {
        UUID userId = UUID.randomUUID();
        String encrypted = crypto.encrypt(RFC_SECRET, userId);

        assertFalse(encrypted.contains(crypto.encodeBase32(RFC_SECRET)));
        assertArrayEquals(RFC_SECRET, crypto.decrypt(encrypted, userId));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(encrypted, UUID.randomUUID()));
        assertNotEquals(encrypted, crypto.encrypt(RFC_SECRET, userId));
    }
}
