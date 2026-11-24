package com.canchaya.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regla por defecto: el anticipo es un porcentaje del total.
 * El porcentaje se configura con la propiedad {@code canchaya.deposit-percentage} (30 % si no se define).
 * @Component: Spring crea esta clase y la inyecta donde se pida un DepositPolicy.
 */
@Component
public class PercentageDepositPolicy implements DepositPolicy {

    private final BigDecimal percentage;

    /** Valida al arrancar que el porcentaje esté entre 0 y 100. */
    public PercentageDepositPolicy(@Value("${canchaya.deposit-percentage:30}") BigDecimal percentage) {
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Deposit percentage must be between 0 and 100");
        }
        this.percentage = percentage;
    }

    /** anticipo = total × porcentaje / 100, redondeado a 2 decimales. Ej.: 80.000 × 30 % = 24.000. */
    @Override
    public BigDecimal depositFor(BigDecimal totalAmount) {
        return totalAmount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
