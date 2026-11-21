package com.claims.api.claim;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.claims.api.claim.dto.AdjudicationResponse;
import com.claims.api.claim.dto.ClaimResponse;
import com.claims.api.common.PageParams;
import com.claims.api.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints for claims adjusters. Requires ROLE_ADJUSTER (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/adjuster/claims")
@Tag(name = "Adjuster claims", description = "Review, adjudicate and pay claims (ADJUSTER role)")
@SecurityRequirement(name = "bearerAuth")
public class AdjusterClaimController {

    private final ClaimService claimService;

    public AdjusterClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    @Operation(summary = "List claims, optionally filtered by status (work queue: status=SUBMITTED, paginated)")
    public PageResponse<ClaimResponse> list(@RequestParam(required = false) ClaimStatus status,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        Sort sort = status == null
                ? Sort.by(Sort.Direction.DESC, "createdAt", "id")
                : Sort.by(Sort.Direction.ASC, "createdAt", "id");
        return PageResponse.from(claimService.listAll(status, PageParams.of(page, size, sort)), c -> c);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any claim")
    public ClaimResponse get(@PathVariable Long id) {
        return claimService.get(id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Start review: SUBMITTED -> IN_REVIEW")
    public ClaimResponse startReview(@PathVariable Long id) {
        return claimService.startReview(id);
    }

    @PostMapping("/{id}/adjudicate")
    @Operation(summary = "Run the rules engine: IN_REVIEW -> APPROVED (with benefit) or DENIED (with reasons)")
    public AdjudicationResponse adjudicate(@PathVariable Long id) {
        return claimService.adjudicate(id);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Mark as paid: APPROVED -> PAID")
    public ClaimResponse pay(@PathVariable Long id) {
        return claimService.markPaid(id);
    }
}
