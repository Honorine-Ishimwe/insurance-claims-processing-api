package com.claims.api.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claims.api.TestData;
import com.claims.api.claim.Claim;
import com.claims.api.policy.CoveredService;
import com.claims.api.policy.CoveredServiceRepository;

@ExtendWith(MockitoExtension.class)
class CoverageRuleTest {

    @Mock
    private CoveredServiceRepository coveredServices;

    @InjectMocks
    private CoverageRule rule;

    private Claim claimFor(String code) {
        LocalDate d = LocalDate.of(2026, 5, 1);
        return TestData.claim(TestData.activePolicy(), code, d, d, "100.00");
    }

    @Test
    void coveredServicePasses() {
        when(coveredServices.findById("99213"))
                .thenReturn(Optional.of(new CoveredService("99213", "Office visit", true)));

        assertThat(rule.evaluate(claimFor("99213")).passed()).isTrue();
    }

    @Test
    void excludedServiceFails() {
        when(coveredServices.findById("15780"))
                .thenReturn(Optional.of(new CoveredService("15780", "Dermabrasion (cosmetic)", false)));

        RuleResult result = rule.evaluate(claimFor("15780"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("not covered").contains("Dermabrasion");
    }

    @Test
    void unknownServiceCodeFails() {
        when(coveredServices.findById("00000")).thenReturn(Optional.empty());

        RuleResult result = rule.evaluate(claimFor("00000"));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("Unknown service code");
    }
}
