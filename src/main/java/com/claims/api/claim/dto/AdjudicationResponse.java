package com.claims.api.claim.dto;

import java.util.List;

import com.claims.api.rules.RuleResult;

/** Result of adjudication: the updated claim plus the outcome of every rule. */
public record AdjudicationResponse(ClaimResponse claim, List<RuleResult> ruleResults) {
}
