package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;

import java.math.BigDecimal;

/**
 * Contrato que cumple todo medio de pago.
 * <p>
 * GRASP Polimorfismo: cada medio de pago cobra a su manera, pero todos responden al mismo
 * mensaje {@link #charge}. En vez de una cadena
 * {@code if (method == WOMPI) ... else if (method == NEQUI) ...}, cada medio es una clase.
 * Agregar uno nuevo (p. ej. PSE) es crear una clase, no editar las que ya existen.
 */
public interface PaymentMethod {

    /** Qué tipo de medio es (NEQUI, DAVIPLATA, ...). Lo usa el resolver para encontrarlo. */
    PaymentMethodType type();

    /**
     * Intenta cobrar un monto.
     * @param amount    valor a cobrar (el anticipo)
     * @param reference referencia de CanchaYa, p. ej. "RES-15"
     * @return resultado del cobro (aprobado o rechazado y su referencia)
     */
    PaymentResult charge(BigDecimal amount, String reference);
}
