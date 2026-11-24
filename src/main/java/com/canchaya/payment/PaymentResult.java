package com.canchaya.payment;

import com.canchaya.domain.PaymentStatus;

/**
 * Lo que responde cualquier medio de pago después de intentar cobrar.
 * Es un record: una clase inmutable que solo guarda datos (Java crea constructor, getters, equals...).
 */
public record PaymentResult(PaymentStatus status, String transactionRef) {

    /** Atajo para crear un resultado aprobado con la referencia de la transacción. */
    public static PaymentResult approved(String transactionRef) {
        return new PaymentResult(PaymentStatus.APPROVED, transactionRef);
    }

    /** Atajo para crear un resultado rechazado; se guarda el motivo como referencia. */
    public static PaymentResult declined(String reason) {
        return new PaymentResult(PaymentStatus.DECLINED, reason);
    }
}
