package com.claims.api.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.claims.api.TestData;

class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed = {2}")
    @CsvSource({
            // the happy paths
            "SUBMITTED, IN_REVIEW, true",
            "IN_REVIEW, APPROVED,  true",
            "IN_REVIEW, DENIED,    true",
            "APPROVED,  PAID,      true",
            // skipping steps
            "SUBMITTED, APPROVED,  false",
            "SUBMITTED, DENIED,    false",
            "SUBMITTED, PAID,      false",
            "IN_REVIEW, PAID,      false",
            // going backwards
            "IN_REVIEW, SUBMITTED, false",
            "APPROVED,  IN_REVIEW, false",
            "APPROVED,  DENIED,    false",
            // terminal states
            "DENIED,    APPROVED,  false",
            "DENIED,    PAID,      false",
            "DENIED,    IN_REVIEW, false",
            "PAID,      APPROVED,  false",
            "PAID,      SUBMITTED, false",
            // self transitions
            "SUBMITTED, SUBMITTED, false",
            "PAID,      PAID,      false"
    })
    void transitionMatrix(ClaimStatus from, ClaimStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void deniedAndPaidAreTerminal() {
        assertThat(ClaimStatus.DENIED.isTerminal()).isTrue();
        assertThat(ClaimStatus.PAID.isTerminal()).isTrue();
        assertThat(ClaimStatus.APPROVED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Claim entity blocks an invalid move and keeps its current status")
    void claimBlocksInvalidTransition() {
        Claim claim = TestData.claim(TestData.activePolicy(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), "100.00");

        assertThatThrownBy(() -> claim.transitionTo(ClaimStatus.PAID))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("PAID");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void claimCannotBeDeniedTwice() {
        Claim claim = TestData.claim(TestData.activePolicy(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), "100.00");
        claim.transitionTo(ClaimStatus.IN_REVIEW);
        claim.deny("test");

        assertThatThrownBy(() -> claim.deny("again"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("final status");
    }
}
