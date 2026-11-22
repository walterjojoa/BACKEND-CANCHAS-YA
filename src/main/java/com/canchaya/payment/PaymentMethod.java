package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;

import java.math.BigDecimal;

/**
 * GRASP Polymorphism: every payment method charges in its own way, but all of them
 * answer the same message {@link #charge}. Instead of an
 * {@code if (method == WOMPI) ... else if (method == NEQUI) ...} chain, each method is
 * a class. Adding a new one (e.g. PSE) means adding a class, not editing existing ones.
 */
public interface PaymentMethod {

    PaymentMethodType type();

    PaymentResult charge(BigDecimal amount, String reference);
}
