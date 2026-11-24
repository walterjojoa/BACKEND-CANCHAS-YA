package com.canchaya.domain;

/** Resultado de un intento de pago. */
public enum PaymentStatus {
    /** La pasarela aprobó el pago: la reserva queda confirmada. */
    APPROVED,
    /** La pasarela rechazó el pago: la reserva sigue pendiente. */
    DECLINED
}
