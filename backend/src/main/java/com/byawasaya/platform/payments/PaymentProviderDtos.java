package com.byawasaya.platform.payments;

import jakarta.validation.constraints.Size;

public final class PaymentProviderDtos {
    private PaymentProviderDtos() {}

    public record ConnectionRequest(
            @Size(max = 100) String merchantCode,
            @Size(max = 500) String secretKey,
            boolean sandbox,
            boolean enabled) {}

    public record ProviderState(boolean configured, boolean enabled, boolean sandbox) {}

    public record Settings(ProviderState esewa, ProviderState khalti, boolean onlinePaymentEnabled) {}

    public record CheckoutResponse(
            com.byawasaya.platform.orders.OrderDtos.OrderResponse order,
            String provider,
            String redirectUrl,
            String redirectMethod,
            java.util.Map<String, String> redirectFields) {}

    public record AttemptState(String status, String provider, String orderNumber, long amountPaisa, String currency) {}
}
