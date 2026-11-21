package com.claims.api.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claims.api.TestData;
import com.claims.api.claim.Claim;
import com.claims.api.claim.ClaimRepository;

@ExtendWith(MockitoExtension.class)
class DuplicateClaimRuleTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 5, 1);

    @Mock
    private ClaimRepository claims;

    @InjectMocks
    private DuplicateClaimRule rule;

    private final Claim claim = TestData.claim(TestData.activePolicy(), SERVICE_DATE, SERVICE_DATE, "150.00");

    @Test
    void noMatchingClaimPasses() {
        when(claims.existsDuplicate(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(false);

        assertThat(rule.evaluate(claim).passed()).isTrue();
    }

    @Test
    void matchingClaimFails() {
        when(claims.existsDuplicate(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(true);

        RuleResult result = rule.evaluate(claim);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("already exists");
    }

    @Test
    void excludesItselfAndMatchesOnAllKeyFields() {
        rule.evaluate(claim);

        verify(claims).existsDuplicate(eq(42L), eq(1L), eq("Test Clinic"), eq("99213"),
                eq(SERVICE_DATE), eq(new BigDecimal("150.00")));
    }
}
