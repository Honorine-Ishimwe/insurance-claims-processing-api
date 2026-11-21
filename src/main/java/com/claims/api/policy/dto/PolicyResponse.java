package com.claims.api.policy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyStatus;

/** A policy with its year-to-date accumulators and what remains for the plan year. */
public record PolicyResponse(
        Long id,
        String policyNumber,
        String memberUsername,
        LocalDate startDate,
        LocalDate endDate,
        PolicyStatus status,
        BigDecimal annualDeductible,
        BigDecimal deductibleMet,
        BigDecimal remainingDeductible,
        BigDecimal coinsuranceRate,
        BigDecimal annualMaximum,
        BigDecimal benefitsPaidYtd,
        BigDecimal remainingAnnualMaximum) {

    public static PolicyResponse from(Policy p) {
        return new PolicyResponse(p.getId(), p.getPolicyNumber(), p.getMember().getUsername(),
                p.getStartDate(), p.getEndDate(), p.getStatus(),
                p.getAnnualDeductible(), p.getDeductibleMet(),
                p.getAnnualDeductible().subtract(p.getDeductibleMet()).max(BigDecimal.ZERO),
                p.getCoinsuranceRate(),
                p.getAnnualMaximum(), p.getBenefitsPaidYtd(),
                p.getAnnualMaximum().subtract(p.getBenefitsPaidYtd()).max(BigDecimal.ZERO));
    }
}
