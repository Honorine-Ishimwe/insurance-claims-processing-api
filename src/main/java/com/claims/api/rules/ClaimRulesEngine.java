package com.claims.api.rules;

import java.util.List;

import org.springframework.stereotype.Service;

import com.claims.api.claim.Claim;

/**
 * Runs every {@link ClaimRule} against a claim and collects all results.
 * Rules are not short-circuited: the adjuster and member see every reason
 * a claim was denied, not just the first one.
 */
@Service
public class ClaimRulesEngine {

    private final List<ClaimRule> rules;

    /** Spring injects every ClaimRule bean, sorted by {@code @Order}. */
    public ClaimRulesEngine(List<ClaimRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RulesEvaluation evaluate(Claim claim) {
        return new RulesEvaluation(rules.stream().map(rule -> rule.evaluate(claim)).toList());
    }

    public List<String> ruleNames() {
        return rules.stream().map(ClaimRule::name).toList();
    }
}
