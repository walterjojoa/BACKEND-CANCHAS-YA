package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Nequi push payment (simulated). */
@Component
public class NequiPaymentMethod implements PaymentMethod {

    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.NEQUI;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        // A real integration would send a push notification to the player's Nequi app.
        return PaymentResult.approved("NEQUI-" + reference + "-" + System.currentTimeMillis());
    }
}
