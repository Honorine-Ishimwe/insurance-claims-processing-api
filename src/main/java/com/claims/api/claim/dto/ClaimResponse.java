package com.claims.api.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.claims.api.claim.Claim;
import com.claims.api.claim.ClaimStatus;

public record ClaimResponse(
        Long id,
        String claimNumber,
        String policyNumber,
        String providerName,
        String serviceCode,
        LocalDate serviceDate,
        LocalDate submissionDate,
        BigDecimal billedAmount,
        ClaimStatus status,
        Set<ClaimStatus> allowedNextStatuses,
        String denialReason,
        Benefit benefit) {

    /** Present only once the claim has been approved. */
    public record Benefit(
            BigDecimal deductibleApplied,
            BigDecimal coinsuranceAmount,
            BigDecimal planPaid,
            BigDecimal memberResponsibility) {
    }

    public static ClaimResponse from(Claim c) {
        Benefit benefit = c.getPlanPaid() == null ? null
                : new Benefit(c.getDeductibleApplied(), c.getCoinsuranceAmount(),
                        c.getPlanPaid(), c.getMemberResponsibility());
        return new ClaimResponse(
                c.getId(),
                c.getClaimNumber(),
                c.getPolicy().getPolicyNumber(),
                c.getProviderName(),
                c.getServiceCode(),
                c.getServiceDate(),
                c.getSubmissionDate(),
                c.getBilledAmount(),
                c.getStatus(),
                c.getStatus().allowedNext(),
                c.getDenialReason(),
                benefit);
    }
}
