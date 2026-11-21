package com.claims.api.policy;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claims.api.policy.dto.PolicyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Member self-service endpoints for viewing their own policies and YTD accumulators. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Member profile", description = "View your own policies and year-to-date benefit progress (MEMBER role)")
@SecurityRequirement(name = "bearerAuth")
public class MemberPolicyController {

    private final PolicyService policyService;

    public MemberPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/policies")
    @Operation(summary = "List my policies, newest plan year first, with remaining deductible and annual maximum")
    public List<PolicyResponse> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        return policyService.listForMember(jwt.getSubject());
    }
}
