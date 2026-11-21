package com.claims.api.rules;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.claims.api.claim.Claim;
import com.claims.api.claim.ClaimRepository;

/**
 * Rejects a claim if another non-denied claim exists for the same member,
 * provider, service code, service date and billed amount.
 */
@Component
@Order(3)
public class DuplicateClaimRule implements ClaimRule {

    public static final String NAME = "DUPLICATE_CLAIM";

    private final ClaimRepository claims;

    public DuplicateClaimRule(ClaimRepository claims) {
        this.claims = claims;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(Claim claim) {
        Long excludeId = claim.getId() == null ? -1L : claim.getId();
        boolean duplicate = claims.existsDuplicate(
                excludeId,
                claim.getPolicy().getMember().getId(),
                claim.getProviderName(),
                claim.getServiceCode(),
                claim.getServiceDate(),
                claim.getBilledAmount());
        return duplicate
                ? RuleResult.fail(NAME, "A matching claim already exists for this provider, service and date")
                : RuleResult.pass(NAME);
    }
}
