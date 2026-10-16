package com.byawasaya.platform.payments;

import com.byawasaya.platform.audit.AuditService;
import com.byawasaya.platform.business.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/payment-providers")
public class PaymentProviderController {
    private final PaymentProviderCredentialsService credentials;
    private final AuditService audit;

    public PaymentProviderController(PaymentProviderCredentialsService credentials, AuditService audit) {
        this.credentials = credentials;
        this.audit = audit;
    }

    @GetMapping
    public PaymentProviderDtos.Settings settings() {
        return credentials.settings(TenantContext.requireBusinessId());
    }

    @PutMapping("/{provider}")
    public PaymentProviderDtos.Settings save(@PathVariable PaymentProvider provider,
            @Valid @RequestBody PaymentProviderDtos.ConnectionRequest request,
            Authentication authentication, jakarta.servlet.http.HttpServletRequest servletRequest) {
        var principal = authentication == null ? null : authentication.getDetails();
        String actor = principal instanceof com.byawasaya.platform.auth.TokenService.TokenPrincipal p ? p.username() : null;
        var businessId = TenantContext.requireBusinessId();
        var result = credentials.save(businessId, provider, request);
        audit.record(actor, "STORE_OWNER", businessId, "PAYMENT_PROVIDER_CONFIGURED", "PAYMENT_PROVIDER", provider.name(), "SUCCESS",
                "{\"enabled\":" + request.enabled() + ",\"sandbox\":" + request.sandbox() + "}", servletRequest);
        return result;
    }
}
