package com.claims.api.rules;

import java.util.List;

/** Results of running every rule against a claim. */
public record RulesEvaluation(List<RuleResult> results) {

    public RulesEvaluation {
        results = List.copyOf(results);
    }

    public boolean allPassed() {
        return results.stream().allMatch(RuleResult::passed);
    }

    public List<RuleResult> failures() {
        return results.stream().filter(r -> !r.passed()).toList();
    }

    /** All failure reasons joined into one human-readable sentence list. */
    public String failureSummary() {
        return failures().stream()
                .map(r -> r.ruleName() + ": " + r.reason())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
