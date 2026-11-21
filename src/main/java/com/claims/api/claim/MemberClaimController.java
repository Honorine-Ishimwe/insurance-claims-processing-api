package com.claims.api.claim;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.claims.api.claim.dto.ClaimResponse;
import com.claims.api.claim.dto.SubmitClaimRequest;
import com.claims.api.common.PageParams;
import com.claims.api.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Endpoints for insured members. Requires ROLE_MEMBER (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Member claims", description = "Submit and track your own claims (MEMBER role)")
@SecurityRequirement(name = "bearerAuth")
public class MemberClaimController {

    private final ClaimService claimService;

    public MemberClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new claim", description = "Claim starts in SUBMITTED status. Submission date is today.")
    public ClaimResponse submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitClaimRequest request) {
        return claimService.submit(jwt.getSubject(), request);
    }

    @GetMapping
    @Operation(summary = "List my claims, newest first (paginated)")
    public PageResponse<ClaimResponse> myClaims(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        var pageable = PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(claimService.listForMember(jwt.getSubject(), pageable), c -> c);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my claims, including the benefit breakdown once approved")
    public ClaimResponse myClaim(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return claimService.getForMember(jwt.getSubject(), id);
    }
}
