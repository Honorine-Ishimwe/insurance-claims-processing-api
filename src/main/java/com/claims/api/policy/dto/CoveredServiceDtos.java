package com.claims.api.policy.dto;

import com.claims.api.policy.CoveredService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CoveredServiceDtos {

    private CoveredServiceDtos() {
    }

    public record CoveredServiceRequest(
            @Schema(example = "Office visit, new patient")
            @NotBlank(message = "description is required")
            @Size(max = 200, message = "description must be at most 200 characters")
            String description,

            @NotNull(message = "covered is required")
            Boolean covered) {
    }

    public record CoveredServiceResponse(String serviceCode, String description, boolean covered) {
        public static CoveredServiceResponse from(CoveredService s) {
            return new CoveredServiceResponse(s.getServiceCode(), s.getDescription(), s.isCovered());
        }
    }
}
