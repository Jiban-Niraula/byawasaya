package com.byawasaya.platform.auth;

public interface EmailMfaProvider {
    boolean isEnabled();

    void sendOtp(String email, String otp);
}
