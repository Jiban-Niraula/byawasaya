package com.byawasaya.platform.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Centralises payment prerequisites that must be satisfied before an order advances. */
final class OrderFulfilmentPolicy {
    private OrderFulfilmentPolicy() {}

    static void validate(PaymentMethod method, PaymentStatus paymentStatus, OrderStatus targetStatus) {
        if (targetStatus == OrderStatus.PROCESSING || targetStatus == OrderStatus.SHIPPED
                || targetStatus == OrderStatus.DELIVERED) {
            if (method == PaymentMethod.ONLINE && paymentStatus != PaymentStatus.PAID) {
                throw conflict("Online orders cannot be fulfilled before payment is confirmed.");
            }
            if (method == PaymentMethod.ADVANCE_COD && paymentStatus != PaymentStatus.PARTIALLY_PAID
                    && paymentStatus != PaymentStatus.PAID) {
                throw conflict("The required advance must be confirmed before fulfilment.");
            }
        }
        if (targetStatus == OrderStatus.DELIVERED && method == PaymentMethod.ADVANCE_COD
                && paymentStatus == PaymentStatus.PARTIALLY_PAID) {
            throw conflict("Record the remaining COD balance before marking this order delivered.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
