package com.claims.api.benefit;

import java.math.BigDecimal;

/**
 * Breakdown of who pays what. Always satisfies:
 * {@code billedAmount = deductibleApplied + coinsuranceAmount + amountOverAnnualMaximum + planPays}
 * and {@code memberResponsibility = billedAmount - planPays}.
 *
 * @param billedAmount            amount billed
 * @param deductibleApplied       portion that went toward the member's deductible
 * @param coinsuranceAmount       member's coinsurance share of the post-deductible amount
 * @param amountOverAnnualMaximum plan share the plan could not pay because the annual maximum was hit
 * @param planPays                amount the plan pays
 * @param memberResponsibility    amount the member owes
 */
public record BenefitResult(
        BigDecimal billedAmount,
        BigDecimal deductibleApplied,
        BigDecimal coinsuranceAmount,
        BigDecimal amountOverAnnualMaximum,
        BigDecimal planPays,
        BigDecimal memberResponsibility) {

    public boolean cappedByAnnualMaximum() {
        return amountOverAnnualMaximum.signum() > 0;
    }
}
