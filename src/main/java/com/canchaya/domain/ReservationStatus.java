package com.canchaya.domain;

/** Estados posibles de una reserva. */
public enum ReservationStatus {
    /** Creada, esperando que se pague el anticipo. */
    PENDING_PAYMENT,
    /** Anticipo pagado: el turno queda asegurado. */
    CONFIRMED,
    /** Cancelada por el jugador: el turno vuelve a quedar libre. */
    CANCELLED
}
