package com.canchaya.payment;

import com.canchaya.domain.PaymentStatus;

/** What any payment method answers after trying to charge. */
public record PaymentResult(PaymentStatus status, String transactionRef) {

    public static PaymentResult approved(String transactionRef) {
        return new PaymentResult(PaymentStatus.APPROVED, transactionRef);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult(PaymentStatus.DECLINED, reason);
    }
}
