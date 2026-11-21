package com.claims.api.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.claims.api.TestData;

class TimelyFilingRuleTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);

    private final TimelyFilingRule rule = new TimelyFilingRule(90);

    private RuleResult submittedAfterDays(int days) {
        return rule.evaluate(TestData.claim(TestData.activePolicy(),
                SERVICE_DATE, SERVICE_DATE.plusDays(days), "100.00"));
    }

    @ParameterizedTest(name = "submitted {0} days after service -> passes = {1}")
    @CsvSource({"0, true", "1, true", "89, true", "90, true", "91, false", "365, false"})
    void filingWindow(int days, boolean expected) {
        assertThat(submittedAfterDays(days).passed()).isEqualTo(expected);
    }

    @Test
    @DisplayName("day 91 failure explains the deadline")
    void lateReasonIsClear() {
        RuleResult result = submittedAfterDays(91);
        assertThat(result.reason()).contains("90-day").contains(SERVICE_DATE.plusDays(90).toString());
    }

    @Test
    @DisplayName("window crosses a leap day correctly (2028-02-29)")
    void leapYear() {
        LocalDate service = LocalDate.of(2028, 1, 1);
        TimelyFilingRule r = new TimelyFilingRule(90);
        // 2028-01-01 + 90 days = 2028-03-31 (leap year)
        assertThat(r.evaluate(TestData.claim(TestData.activePolicy(), service, LocalDate.of(2028, 3, 31), "1")).passed())
                .isTrue();
        assertThat(r.evaluate(TestData.claim(TestData.activePolicy(), service, LocalDate.of(2028, 4, 1), "1")).passed())
                .isFalse();
    }

    @Test
    @DisplayName("limit is configurable")
    void configurableLimit() {
        TimelyFilingRule strict = new TimelyFilingRule(30);
        assertThat(strict.evaluate(TestData.claim(TestData.activePolicy(),
                SERVICE_DATE, SERVICE_DATE.plusDays(31), "100.00")).passed()).isFalse();
    }
}
