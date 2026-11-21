package com.claims.api.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.claims.api.TestData;
import com.claims.api.claim.Claim;
import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyStatus;

/** Coverage period used: 2026-01-01 to 2026-12-31, both inclusive. */
class EligibilityRuleTest {

    private final EligibilityRule rule = new EligibilityRule();

    private RuleResult evaluate(Policy policy, LocalDate serviceDate) {
        Claim claim = TestData.claim(policy, serviceDate, serviceDate, "100.00");
        return rule.evaluate(claim);
    }

    @Test
    @DisplayName("service on the last day of coverage is eligible")
    void lastDayOfCoverage() {
        assertThat(evaluate(TestData.activePolicy(), TestData.PLAN_END).passed()).isTrue();
    }

    @Test
    @DisplayName("service on the first day of coverage is eligible")
    void firstDayOfCoverage() {
        assertThat(evaluate(TestData.activePolicy(), TestData.PLAN_START).passed()).isTrue();
    }

    @Test
    @DisplayName("service the day after coverage ends is not eligible")
    void dayAfterCoverageEnds() {
        RuleResult result = evaluate(TestData.activePolicy(), TestData.PLAN_END.plusDays(1));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("outside coverage period");
    }

    @Test
    @DisplayName("service the day before coverage starts is not eligible")
    void dayBeforeCoverageStarts() {
        assertThat(evaluate(TestData.activePolicy(), TestData.PLAN_START.minusDays(1)).passed()).isFalse();
    }

    @Test
    @DisplayName("lapsed policy is not eligible even inside the coverage dates")
    void lapsedPolicy() {
        Policy lapsed = TestData.policy(PolicyStatus.LAPSED, "0.00", "0.00");
        RuleResult result = evaluate(lapsed, LocalDate.of(2026, 6, 1));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("LAPSED");
    }

    @Test
    void cancelledPolicy() {
        Policy cancelled = TestData.policy(PolicyStatus.CANCELLED, "0.00", "0.00");
        assertThat(evaluate(cancelled, LocalDate.of(2026, 6, 1)).passed()).isFalse();
    }
}
