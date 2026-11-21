package com.claims.api.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.claims.api.TestData;
import com.claims.api.claim.Claim;

/** The engine only knows about the ClaimRule interface, so rules are mocked here. */
class ClaimRulesEngineTest {

    private final Claim claim = TestData.claim(TestData.activePolicy(),
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), "100.00");

    private static ClaimRule ruleReturning(RuleResult result) {
        ClaimRule rule = mock(ClaimRule.class);
        when(rule.evaluate(any())).thenReturn(result);
        return rule;
    }

    @Test
    void allRulesPass() {
        ClaimRulesEngine engine = new ClaimRulesEngine(List.of(
                ruleReturning(RuleResult.pass("A")), ruleReturning(RuleResult.pass("B"))));

        RulesEvaluation evaluation = engine.evaluate(claim);

        assertThat(evaluation.allPassed()).isTrue();
        assertThat(evaluation.failures()).isEmpty();
        assertThat(evaluation.results()).hasSize(2);
    }

    @Test
    @DisplayName("does not stop at the first failure: every reason is reported")
    void collectsAllFailures() {
        ClaimRule a = ruleReturning(RuleResult.fail("A", "first problem"));
        ClaimRule b = ruleReturning(RuleResult.pass("B"));
        ClaimRule c = ruleReturning(RuleResult.fail("C", "second problem"));
        ClaimRulesEngine engine = new ClaimRulesEngine(List.of(a, b, c));

        RulesEvaluation evaluation = engine.evaluate(claim);

        assertThat(evaluation.allPassed()).isFalse();
        assertThat(evaluation.failures()).extracting(RuleResult::ruleName).containsExactly("A", "C");
        assertThat(evaluation.failureSummary()).isEqualTo("A: first problem; C: second problem");
        verify(a).evaluate(claim);
        verify(b).evaluate(claim);
        verify(c).evaluate(claim);
    }

    @Test
    @DisplayName("results come back in the order the rules were given")
    void preservesOrder() {
        ClaimRulesEngine engine = new ClaimRulesEngine(List.of(
                ruleReturning(RuleResult.pass("ELIGIBILITY")),
                ruleReturning(RuleResult.pass("COVERAGE")),
                ruleReturning(RuleResult.pass("TIMELY_FILING"))));

        assertThat(engine.evaluate(claim).results())
                .extracting(RuleResult::ruleName)
                .containsExactly("ELIGIBILITY", "COVERAGE", "TIMELY_FILING");
    }
}
