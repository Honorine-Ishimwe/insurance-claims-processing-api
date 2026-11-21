package com.claims.api.rules;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.claims.api.claim.Claim;

/**
 * The claim must be submitted within N days of the service date (default 90).
 * Day N itself is still on time; day N+1 is late.
 */
@Component
@Order(4)
public class TimelyFilingRule implements ClaimRule {

    public static final String NAME = "TIMELY_FILING";

    private final int filingLimitDays;

    public TimelyFilingRule(@Value("${claims.rules.timely-filing-days:90}") int filingLimitDays) {
        this.filingLimitDays = filingLimitDays;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RuleResult evaluate(Claim claim) {
        LocalDate deadline = claim.getServiceDate().plusDays(filingLimitDays);
        if (claim.getSubmissionDate().isAfter(deadline)) {
            return RuleResult.fail(NAME, "Submitted " + claim.getSubmissionDate()
                    + ", after the " + filingLimitDays + "-day filing deadline of " + deadline);
        }
        return RuleResult.pass(NAME);
    }
}
