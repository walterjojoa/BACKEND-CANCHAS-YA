package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Daviplata payment (simulated). It was added later without editing any other class:
 * that is the benefit of GRASP Polymorphism.
 */
@Component
public class DaviplataPaymentMethod implements PaymentMethod {

    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.DAVIPLATA;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        return PaymentResult.approved("DAVI-" + reference + "-" + System.currentTimeMillis());
    }
}
