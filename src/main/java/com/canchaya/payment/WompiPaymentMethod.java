package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Card payments through Wompi.
 * <p>
 * GRASP Indirection: this class is the intermediary between our business (amount in
 * pesos + a reference) and Wompi's API (amount in cents + currency code). The rest of
 * the system never sees {@link WompiClient}.
 */
@Component
public class WompiPaymentMethod implements PaymentMethod {

    private final WompiClient wompi;

    public WompiPaymentMethod(WompiClient wompi) {
        this.wompi = wompi;
    }

    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.WOMPI;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        long cents = amount.movePointRight(2).longValueExact();
        return PaymentResult.approved(wompi.createTransaction(cents, "COP", reference));
    }
}
