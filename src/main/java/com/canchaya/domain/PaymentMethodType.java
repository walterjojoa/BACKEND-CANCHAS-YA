package com.canchaya.domain;

/** Medios de pago que acepta CanchaYa para el anticipo. */
public enum PaymentMethodType {
    /** Pasarela Wompi (tarjeta, PSE, Nequi...) mediante su Web Checkout. */
    WOMPI,
    /** Billetera Nequi (simulada en esta versión). */
    NEQUI,
    /** Billetera Daviplata (simulada en esta versión). */
    DAVIPLATA
}
