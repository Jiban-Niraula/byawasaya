package com.byawasaya.platform.orders;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrderFulfilmentPolicyTest {
    @Test
    void onlineOrderCannotEnterFulfilmentUntilPaymentIsConfirmed() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> OrderFulfilmentPolicy.validate(PaymentMethod.ONLINE, PaymentStatus.PAYMENT_PENDING,
                        OrderStatus.PROCESSING));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void onlineOrderCanEnterFulfilmentOncePaid() {
        assertDoesNotThrow(() -> OrderFulfilmentPolicy.validate(PaymentMethod.ONLINE, PaymentStatus.PAID,
                OrderStatus.PROCESSING));
    }

    @Test
    void advanceCodRequiresAdvanceBeforeFulfilment() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> OrderFulfilmentPolicy.validate(PaymentMethod.ADVANCE_COD, PaymentStatus.PAYMENT_PENDING,
                        OrderStatus.PROCESSING));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void partialAdvanceCodMayShipButCannotBeDeliveredBeforeBalanceReceipt() {
        assertDoesNotThrow(() -> OrderFulfilmentPolicy.validate(PaymentMethod.ADVANCE_COD,
                PaymentStatus.PARTIALLY_PAID, OrderStatus.SHIPPED));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> OrderFulfilmentPolicy.validate(PaymentMethod.ADVANCE_COD, PaymentStatus.PARTIALLY_PAID,
                        OrderStatus.DELIVERED));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void cashOnDeliveryDoesNotRequirePrepayment() {
        assertDoesNotThrow(() -> OrderFulfilmentPolicy.validate(PaymentMethod.COD, PaymentStatus.UNPAID,
                OrderStatus.PROCESSING));
    }
}
