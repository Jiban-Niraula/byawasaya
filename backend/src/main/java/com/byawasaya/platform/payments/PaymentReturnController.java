package com.byawasaya.platform.payments;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentReturnController {
    private final PaymentGatewayService payments;

    public PaymentReturnController(PaymentGatewayService payments) { this.payments = payments; }

    @GetMapping("/api/store/payments/return")
    public ResponseEntity<Void> providerReturn(@RequestParam UUID attemptId,
            @RequestParam(required = false) String pidx,
            @RequestParam(required = false) String data) {
        URI location = payments.verifyCallback(attemptId, pidx, data);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    @GetMapping("/api/store/{slug}/payments/{attemptId}")
    public PaymentProviderDtos.AttemptState state(@PathVariable String slug, @PathVariable UUID attemptId) {
        return payments.state(slug, attemptId);
    }
}
