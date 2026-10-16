package com.byawasaya.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CustomerAuthDtos {
    private CustomerAuthDtos() {}

    public record RegisterRequest(@NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 30) String phone) {}
    public record LoginRequest(@NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(max = 72) String password) {}
    public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {}
    public record EmailRequest(@NotBlank @Email @Size(max = 180) String email) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 30, max = 200) String token,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    public record AuthResponse(String message) {}
    public record LoginResponse(long expiresIn, CustomerInfo user, boolean mfaRequired, String challengeToken) {}
    public record CustomerInfo(UUID id, String email, String fullName, boolean emailVerified) {}
    public record SessionResponse(boolean authenticated, CustomerInfo user, Instant expiresAt) {}
    public record OrderHistory(UUID id, UUID businessId, String storeName, String storeSlug, String orderNumber,
            java.math.BigDecimal totalAmount, String paymentStatus, String orderStatus, Instant createdAt) {}
}
