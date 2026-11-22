package com.canchaya.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Default rule: the deposit is a percentage of the total (configurable, 30 % by default). */
@Component
public class PercentageDepositPolicy implements DepositPolicy {

    private final BigDecimal percentage;

    public PercentageDepositPolicy(@Value("${canchaya.deposit-percentage:30}") BigDecimal percentage) {
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Deposit percentage must be between 0 and 100");
        }
        this.percentage = percentage;
    }

    @Override
    public BigDecimal depositFor(BigDecimal totalAmount) {
        return totalAmount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
