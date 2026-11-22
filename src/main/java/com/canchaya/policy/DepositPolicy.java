package com.canchaya.policy;

import java.math.BigDecimal;

/**
 * GRASP Protected Variations: how much deposit ("anticipo") a player pays is a business
 * decision that is still under discussion (30 %? fixed amount? nothing on weekdays?).
 * Hiding it behind this stable interface means changing the rule never touches
 * {@code Field}, {@code Reservation} or the services.
 */
public interface DepositPolicy {

    BigDecimal depositFor(BigDecimal totalAmount);
}
