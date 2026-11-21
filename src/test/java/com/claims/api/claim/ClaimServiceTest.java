package com.claims.api.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.claims.api.TestData;
import com.claims.api.audit.AuditService;
import com.claims.api.benefit.BenefitCalculator;
import com.claims.api.claim.dto.AdjudicationResponse;
import com.claims.api.claim.dto.SubmitClaimRequest;
import com.claims.api.common.NotFoundException;
import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyRepository;
import com.claims.api.policy.PolicyStatus;
import com.claims.api.rules.ClaimRulesEngine;
import com.claims.api.rules.RuleResult;
import com.claims.api.rules.RulesEvaluation;
import com.claims.api.user.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claims;
    @Mock
    private PolicyRepository policies;
    @Mock
    private AppUserRepository users;
    @Mock
    private ClaimRulesEngine rulesEngine;
    @Mock
    private AuditService audit;

    private ClaimService service;
    private Policy policy;
    private Claim claim;

    @BeforeEach
    void setUp() {
        // Real calculator: it is pure, so there is no reason to mock it.
        service = new ClaimService(claims, policies, users, rulesEngine, new BenefitCalculator(), audit, Clock.systemUTC());
        policy = TestData.policy(PolicyStatus.ACTIVE, "300.00", "1000.00");
        claim = TestData.claim(policy, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), "1000.00");
    }

    @Test
    @DisplayName("approval stores the benefit on the claim and updates the policy accumulators")
    void approveUpdatesAccumulators() {
        claim.transitionTo(ClaimStatus.IN_REVIEW);
        when(claims.findById(42L)).thenReturn(Optional.of(claim));
        when(rulesEngine.evaluate(claim)).thenReturn(new RulesEvaluation(List.of(RuleResult.pass("ALL"))));

        AdjudicationResponse response = service.adjudicate(42L);

        // $200 deductible left; $800 * 80% = $640 plan; member $360
        assertThat(response.claim().status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.claim().benefit().planPaid()).isEqualByComparingTo("640.00");
        assertThat(response.claim().benefit().memberResponsibility()).isEqualByComparingTo("360.00");
        assertThat(policy.getDeductibleMet()).isEqualByComparingTo("500.00");
        assertThat(policy.getBenefitsPaidYtd()).isEqualByComparingTo("1640.00");
    }

    @Test
    @DisplayName("a failed rule denies the claim with the reason and leaves accumulators untouched")
    void denyLeavesAccumulatorsAlone() {
        claim.transitionTo(ClaimStatus.IN_REVIEW);
        when(claims.findById(42L)).thenReturn(Optional.of(claim));
        when(rulesEngine.evaluate(claim)).thenReturn(new RulesEvaluation(List.of(
                RuleResult.pass("ELIGIBILITY"), RuleResult.fail("TIMELY_FILING", "too late"))));

        AdjudicationResponse response = service.adjudicate(42L);

        assertThat(response.claim().status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(response.claim().denialReason()).isEqualTo("TIMELY_FILING: too late");
        assertThat(response.claim().benefit()).isNull();
        assertThat(policy.getDeductibleMet()).isEqualByComparingTo("300.00");
        assertThat(policy.getBenefitsPaidYtd()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("adjudicating a claim that is not IN_REVIEW is rejected before any rule runs")
    void adjudicateRequiresInReview() {
        when(claims.findById(42L)).thenReturn(Optional.of(claim)); // still SUBMITTED

        assertThatThrownBy(() -> service.adjudicate(42L))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verifyNoInteractions(rulesEngine);
    }

    @Test
    void cannotPayADeniedClaim() {
        claim.transitionTo(ClaimStatus.IN_REVIEW);
        claim.deny("nope");
        when(claims.findById(42L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.markPaid(42L)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("a member cannot file against another member's policy (reported as not found)")
    void submitRejectsSomeoneElsesPolicy() {
        when(users.findByUsername("member99")).thenReturn(Optional.of(TestData.member(99L)));
        when(policies.findByPolicyNumber("POL-TEST")).thenReturn(Optional.of(policy)); // owned by member 1
        SubmitClaimRequest request = new SubmitClaimRequest("POL-TEST", "Clinic", "99213",
                LocalDate.of(2026, 5, 1), new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.submit("member99", request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("POL-TEST");
        verify(claims, never()).save(any());
    }
}
