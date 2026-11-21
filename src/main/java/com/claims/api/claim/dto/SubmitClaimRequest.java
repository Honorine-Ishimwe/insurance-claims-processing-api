package com.claims.api.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitClaimRequest(

        @Schema(example = "POL-1001")
        @NotBlank(message = "policyNumber is required")
        String policyNumber,

        @Schema(example = "Riverside Family Clinic")
        @NotBlank(message = "providerName is required")
        @Size(max = 200, message = "providerName must be at most 200 characters")
        String providerName,

        @Schema(example = "99213", description = "5-digit CPT service code")
        @NotBlank(message = "serviceCode is required")
        @Pattern(regexp = "\\d{5}", message = "serviceCode must be a 5-digit code")
        String serviceCode,

        @Schema(example = "2026-10-01")
        @NotNull(message = "serviceDate is required")
        @PastOrPresent(message = "serviceDate cannot be in the future")
        LocalDate serviceDate,

        @Schema(example = "250.00")
        @NotNull(message = "billedAmount is required")
        @DecimalMin(value = "0.01", message = "billedAmount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "billedAmount must have at most 2 decimal places")
        BigDecimal billedAmount) {
}
