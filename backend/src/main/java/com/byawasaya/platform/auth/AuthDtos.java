package com.byawasaya.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Size(max=180) String identifier,
                               @NotBlank @Size(max=72) String password,
                               @NotBlank @Pattern(regexp="PLATFORM|TENANT") String accountType) {}
    public record RegisterStoreRequest(
        @NotBlank @Size(max=120) String storeName,
        @NotBlank @Size(max=120) String ownerName,
        @NotBlank @Email @Size(max=180) String email,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9][a-zA-Z0-9-]{2,59}", message="Use 3–60 letters, numbers or hyphens for the store URL.") String slug,
        @NotBlank @Size(min=8, max=72) String password,
        @NotBlank @Size(max=30) String phone) {}
    public record LoginResponse(long expiresIn, UserInfo user, boolean mfaRequired, String challengeToken, String challengeMethod) { public LoginResponse(long expiresIn, UserInfo user, boolean mfaRequired, String challengeToken) { this(expiresIn, user, mfaRequired, challengeToken, null); } }
    public record UserInfo(String username, String role, java.util.UUID businessId, String storeSlug, String storeStatus, boolean emailVerified, boolean mfaEnabled) {}
    public record SessionResponse(boolean authenticated, UserInfo user, java.time.Instant expiresAt) {}
    public record RegistrationResponse(String status, String message) {}
    public record CsrfResponse(String token) {}
    public record MfaStatus(boolean enabled, int recoveryCodesRemaining) {}
    public record MfaEnrollment(String secret, String provisioningUri, java.time.Instant expiresAt) {}
    public record MfaEnableRequest(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record MfaRecoveryCodes(java.util.List<String> recoveryCodes) {}
    public record MfaEnrollmentRequest(@NotBlank @Size(max=72) String password) {}
    public record MfaLoginRequest(@NotBlank @Size(max=128) String challengeToken, @NotBlank @Size(max=32) String code) {}
    public record MfaDisableRequest(@NotBlank @Size(max=72) String password, @NotBlank @Size(max=32) String code) {}
}
