package com.claims.api.benefit;

import java.math.BigDecimal;

/**
 * Everything the benefit calculation needs, as plain values. Keeping the
 * calculator independent of JPA entities makes it trivially unit-testable.
 *
 * @param billedAmount     amount billed for this claim
 * @param annualDeductible plan-year deductible
 * @param deductibleMet    deductible already satisfied this plan year
 * @param coinsuranceRate  share the plan pays after the deductible (0.80 = 80%)
 * @param annualMaximum    most the plan will pay in a plan year
 * @param benefitsPaidYtd  amount the plan has already paid this plan year
 */
public record BenefitInput(
        BigDecimal billedAmount,
        BigDecimal annualDeductible,
        BigDecimal deductibleMet,
        BigDecimal coinsuranceRate,
        BigDecimal annualMaximum,
        BigDecimal benefitsPaidYtd) {
}
