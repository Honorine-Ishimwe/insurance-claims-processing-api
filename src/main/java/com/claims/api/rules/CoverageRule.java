package com.claims.api.rules;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.claims.api.claim.Claim;
import com.claims.api.policy.CoveredService;
import com.claims.api.policy.CoveredServiceRepository;

/** The service code must exist in the catalog and be marked as covered. */
@Component
@Order(2)
public class CoverageRule implements ClaimRule {

    public static final String NAME = "COVERAGE";

    private final CoveredServiceRepository coveredServices;

    public CoverageRule(CoveredServiceRepository coveredServices) {
        this.coveredServices = coveredServices;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(Claim claim) {
        String code = claim.getServiceCode();
        return coveredServices.findById(code)
                .map(service -> service.isCovered()
                        ? RuleResult.pass(NAME)
                        : RuleResult.fail(NAME, describe(service) + " is not covered by the plan"))
                .orElseGet(() -> RuleResult.fail(NAME, "Unknown service code " + code));
    }

    private static String describe(CoveredService service) {
        return "Service " + service.getServiceCode() + " (" + service.getDescription() + ")";
    }
}
