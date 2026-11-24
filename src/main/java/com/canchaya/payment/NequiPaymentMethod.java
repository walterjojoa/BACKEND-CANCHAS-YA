package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pago push con Nequi (simulado). Una de las implementaciones polimórficas de PaymentMethod.
 * Como siempre aprueba, solo existe si canchaya.payments.simulated-enabled=true
 * (desarrollo y pruebas); en producción el pago real va por Wompi.
 */
@Component
@ConditionalOnProperty(name = "canchaya.payments.simulated-enabled", havingValue = "true")
public class NequiPaymentMethod implements PaymentMethod {

    /** Tipo de medio de pago que atiende esta clase. */
    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.NEQUI;
    }

    /** Simulación: siempre aprueba y genera una referencia única con la hora actual. */
    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        // Una integración real enviaría una notificación push a la app Nequi del jugador.
        return PaymentResult.approved("NEQUI-" + reference + "-" + System.currentTimeMillis());
    }
}
