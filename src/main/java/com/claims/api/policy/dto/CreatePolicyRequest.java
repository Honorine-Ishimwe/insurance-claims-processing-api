package com.claims.api.policy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Admin creates a plan-year policy for an existing MEMBER account. Starts ACTIVE with zero accumulators. */
public record CreatePolicyRequest(
        @Schema(example = "POL-2001")
        @NotBlank(message = "policyNumber is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{3,50}$",
                message = "policyNumber must be 3-50 characters: letters, digits or hyphen")
        String policyNumber,

        @Schema(example = "jdoe")
        @NotBlank(message = "memberUsername is required")
        String memberUsername,

        @Schema(example = "2026-01-01")
        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @Schema(example = "2026-12-31")
        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @Schema(example = "500.00")
        @NotNull(message = "annualDeductible is required")
        @DecimalMin(value = "0.00", message = "annualDeductible cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "annualDeductible must have at most 2 decimal places")
        BigDecimal annualDeductible,

        @Schema(example = "0.80", description = "Share the plan pays after the deductible, 0 to 1")
        @NotNull(message = "coinsuranceRate is required")
        @DecimalMin(value = "0.0", message = "coinsuranceRate must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "coinsuranceRate must be between 0 and 1")
        @Digits(integer = 1, fraction = 4, message = "coinsuranceRate must have at most 4 decimal places")
        BigDecimal coinsuranceRate,

        @Schema(example = "10000.00")
        @NotNull(message = "annualMaximum is required")
        @DecimalMin(value = "0.00", message = "annualMaximum cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "annualMaximum must have at most 2 decimal places")
        BigDecimal annualMaximum) {

    @AssertTrue(message = "endDate must be on or after startDate")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
