package com.claims.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.claims.api.claim.Claim;
import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyStatus;
import com.claims.api.user.AppUser;
import com.claims.api.user.Role;

/** Small builders for unit tests. Plan year 2026, $500 deductible, 80% coinsurance, $10,000 max. */
public final class TestData {

    public static final LocalDate PLAN_START = LocalDate.of(2026, 1, 1);
    public static final LocalDate PLAN_END = LocalDate.of(2026, 12, 31);

    private TestData() {
    }

    public static AppUser member(long id) {
        AppUser user = new AppUser("member" + id, "hash", Role.MEMBER, "Test Member");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Policy activePolicy() {
        return policy(PolicyStatus.ACTIVE, "0.00", "0.00");
    }

    public static Policy policy(PolicyStatus status, String deductibleMet, String benefitsPaidYtd) {
        return new Policy("POL-TEST", member(1L), PLAN_START, PLAN_END, status,
                new BigDecimal("500.00"), new BigDecimal(deductibleMet),
                new BigDecimal("0.8000"), new BigDecimal("10000.00"), new BigDecimal(benefitsPaidYtd));
    }

    public static Claim claim(Policy policy, LocalDate serviceDate, LocalDate submissionDate, String amount) {
        return claim(policy, "99213", serviceDate, submissionDate, amount);
    }

    public static Claim claim(Policy policy, String serviceCode, LocalDate serviceDate,
                              LocalDate submissionDate, String amount) {
        Claim claim = new Claim("CLM-TEST", policy, "Test Clinic", serviceCode,
                serviceDate, submissionDate, new BigDecimal(amount));
        ReflectionTestUtils.setField(claim, "id", 42L);
        return claim;
    }
}
