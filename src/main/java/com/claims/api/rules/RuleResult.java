package com.claims.api.rules;

/** Outcome of a single rule. */
public record RuleResult(String ruleName, boolean passed, String reason) {

    public static RuleResult pass(String ruleName) {
        return new RuleResult(ruleName, true, null);
    }

    public static RuleResult fail(String ruleName, String reason) {
        return new RuleResult(ruleName, false, reason);
    }
}
