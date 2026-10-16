package com.byawasaya.platform.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailMfaProvider implements EmailMfaProvider {
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String from;
    private final boolean enabled;

    public SmtpEmailMfaProvider(ObjectProvider<JavaMailSender> mailProvider,
            @Value("${app.auth.email-mfa.from:${app.mail.from:no-reply@byawasaya.local}}") String from,
            @Value("${app.auth.email-mfa.enabled:true}") boolean enabled) {
        this.mailProvider = mailProvider;
        this.from = from;
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled && mailProvider.getIfAvailable() != null;
    }

    @Override
    public void sendOtp(String email, String otp) {
        if (!isEnabled()) {
            throw new IllegalStateException("Email MFA delivery is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your Byawasaya sign-in code");
        message.setText("Your Byawasaya verification code is " + otp
                + ". It expires in 5 minutes and can only be used once.");
        mailProvider.getObject().send(message);
    }
}
