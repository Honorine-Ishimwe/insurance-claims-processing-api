package com.claims.api.rules;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.claims.api.claim.Claim;
import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyStatus;

/** The policy must be active and must cover the date of service (both ends inclusive). */
@Component
@Order(1)
public class EligibilityRule implements ClaimRule {

    public static final String NAME = "ELIGIBILITY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(Claim claim) {
        Policy policy = claim.getPolicy();
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            return RuleResult.fail(NAME, "Policy " + policy.getPolicyNumber() + " is " + policy.getStatus());
        }
        if (!policy.coversDate(claim.getServiceDate())) {
            return RuleResult.fail(NAME, "Service date " + claim.getServiceDate()
                    + " is outside coverage period " + policy.getStartDate() + " to " + policy.getEndDate());
        }
        return RuleResult.pass(NAME);
    }
}
