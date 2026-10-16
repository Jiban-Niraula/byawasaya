package com.byawasaya.platform.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MetaWhatsAppCloudApiProvider implements WhatsAppMfaProvider {
    private final boolean enabled;
    private final String graphApiUrl;
    private final String graphApiVersion;
    private final String phoneNumberId;
    private final String accessToken;
    private final String templateName;
    private final String templateLanguage;
    private final RestClient client;

    public MetaWhatsAppCloudApiProvider(
            @Value("${app.auth.whatsapp.enabled:false}") boolean enabled,
            @Value("${app.auth.whatsapp.graph-api-url:https://graph.facebook.com}") String graphApiUrl,
            @Value("${app.auth.whatsapp.graph-api-version:v23.0}") String graphApiVersion,
            @Value("${app.auth.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${app.auth.whatsapp.access-token:}") String accessToken,
            @Value("${app.auth.whatsapp.template-name:platform_login_otp}") String templateName,
            @Value("${app.auth.whatsapp.template-language:en_US}") String templateLanguage) {
        this.enabled = enabled;
        this.graphApiUrl = graphApiUrl.replaceAll("/+$", "");
        this.graphApiVersion = graphApiVersion;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.templateName = templateName;
        this.templateLanguage = templateLanguage;
        this.client = RestClient.builder().build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void sendOtp(String phoneNumber, String otp) {
        if (!enabled) throw new IllegalStateException("WhatsApp MFA is not enabled.");
        if (phoneNumberId.isBlank() || accessToken.isBlank() || templateName.isBlank())
            throw new IllegalStateException("WhatsApp MFA is enabled but its Meta configuration is incomplete.");
        client.post()
                .uri(graphApiUrl + "/" + graphApiVersion + "/" + phoneNumberId + "/messages")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "messaging_product", "whatsapp",
                        "to", phoneNumber,
                        "type", "template",
                        "template", Map.of(
                                "name", templateName,
                                "language", Map.of("code", templateLanguage),
                                "components", java.util.List.of(Map.of(
                                        "type", "body",
                                        "parameters", java.util.List.of(Map.of("type", "text", "text", otp)))))))
                .retrieve()
                .toBodilessEntity();
    }
}
