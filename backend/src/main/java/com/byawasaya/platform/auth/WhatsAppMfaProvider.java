package com.byawasaya.platform.auth;

public interface WhatsAppMfaProvider {
    boolean isEnabled();
    void sendOtp(String phoneNumber, String otp);
}
