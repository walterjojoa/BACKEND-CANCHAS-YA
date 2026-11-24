package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pago con Daviplata (simulado). Se agregó después sin editar ninguna otra clase:
 * ese es el beneficio de GRASP Polimorfismo.
 * @Component: Spring lo detecta solo y lo registra en el PaymentMethodResolver.
 * Como siempre aprueba, solo existe si canchaya.payments.simulated-enabled=true
 * (desarrollo y pruebas); en producción el pago real va por Wompi.
 */
@Component
@ConditionalOnProperty(name = "canchaya.payments.simulated-enabled", havingValue = "true")
public class DaviplataPaymentMethod implements PaymentMethod {

    /** Tipo de medio de pago que atiende esta clase. */
    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.DAVIPLATA;
    }

    /** Simulación: siempre aprueba y genera una referencia única con la hora actual. */
    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        return PaymentResult.approved("DAVI-" + reference + "-" + System.currentTimeMillis());
    }
}
