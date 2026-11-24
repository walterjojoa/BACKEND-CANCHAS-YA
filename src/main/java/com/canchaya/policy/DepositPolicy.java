package com.canchaya.policy;

import java.math.BigDecimal;

/**
 * Regla para calcular el anticipo que paga el jugador al reservar.
 * <p>
 * GRASP Variaciones Protegidas: cuánto anticipo se cobra es una decisión de negocio que
 * todavía está en discusión (¿30 %? ¿un valor fijo? ¿nada entre semana?).
 * Esconderla detrás de esta interfaz estable hace que cambiar la regla nunca toque
 * {@code Field}, {@code Reservation} ni los servicios: solo se crea otra implementación.
 */
public interface DepositPolicy {

    /** Devuelve el anticipo a cobrar para un valor total dado. */
    BigDecimal depositFor(BigDecimal totalAmount);
}
