package com.claims.api.rules;

import com.claims.api.claim.Claim;

/**
 * Strategy interface for one adjudication rule. Each rule is a Spring bean;
 * the {@link ClaimRulesEngine} receives all of them automatically, so adding a
 * rule means adding one class – no existing code changes.
 *
 * Use {@link org.springframework.core.annotation.Order} to control evaluation order.
 */
public interface ClaimRule {

    /** Short, stable identifier shown in API responses, e.g. "TIMELY_FILING". */
    String name();

    RuleResult evaluate(Claim claim);
}
