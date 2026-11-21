package com.claims.api.benefit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Plan used unless stated otherwise: $500 deductible, plan pays 80%, $10,000 annual maximum.
 */
class BenefitCalculatorTest {

    private final BenefitCalculator calculator = new BenefitCalculator();

    private static BenefitInput input(String billed, String deductibleMet, String paidYtd) {
        return new BenefitInput(bd(billed), bd("500.00"), bd(deductibleMet), bd("0.80"), bd("10000.00"), bd(paidYtd));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertReconciles(BenefitResult r) {
        assertThat(r.deductibleApplied().add(r.coinsuranceAmount()).add(r.amountOverAnnualMaximum()).add(r.planPays()))
                .as("deductible + coinsurance + over-max + plan = billed")
                .isEqualByComparingTo(r.billedAmount());
        assertThat(r.planPays().add(r.memberResponsibility()))
                .as("plan + member = billed")
                .isEqualByComparingTo(r.billedAmount());
    }

    @Nested
    @DisplayName("Deductible")
    class Deductible {

        @Test
        @DisplayName("claim smaller than remaining deductible: member pays everything")
        void belowDeductible() {
            BenefitResult r = calculator.calculate(input("300.00", "0.00", "0.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("300.00");
            assertThat(r.planPays()).isEqualByComparingTo("0.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("300.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("claim exactly reaches the deductible: plan pays nothing, nothing left for coinsurance")
        void exactlyReachesDeductible() {
            BenefitResult r = calculator.calculate(input("500.00", "0.00", "0.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("500.00");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("0.00");
            assertThat(r.planPays()).isEqualByComparingTo("0.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("500.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("deductible already exactly met: next claim goes straight to coinsurance")
        void deductibleAlreadyMet() {
            BenefitResult r = calculator.calculate(input("100.00", "500.00", "0.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("0.00");
            assertThat(r.planPays()).isEqualByComparingTo("80.00");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("20.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("claim crosses the deductible: remainder split 80/20")
        void crossesDeductible() {
            BenefitResult r = calculator.calculate(input("1000.00", "0.00", "0.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("500.00");
            assertThat(r.planPays()).isEqualByComparingTo("400.00");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("100.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("600.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("deductible partly met earlier in the year")
        void partiallyMet() {
            BenefitResult r = calculator.calculate(input("1000.00", "300.00", "0.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("200.00");
            assertThat(r.planPays()).isEqualByComparingTo("640.00");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("160.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("360.00");
            assertReconciles(r);
        }
    }

    @Nested
    @DisplayName("Annual maximum")
    class AnnualMaximum {

        @Test
        @DisplayName("claim hits the annual maximum: plan pays only what is left, member owes the rest")
        void hitsMaximumMidClaim() {
            BenefitResult r = calculator.calculate(input("1000.00", "500.00", "9900.00"));

            assertThat(r.planPays()).isEqualByComparingTo("100.00");
            assertThat(r.amountOverAnnualMaximum()).isEqualByComparingTo("700.00");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("200.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("900.00");
            assertThat(r.cappedByAnnualMaximum()).isTrue();
            assertReconciles(r);
        }

        @Test
        @DisplayName("plan share exactly equals what is left of the maximum: paid in full, not capped")
        void exactlyReachesMaximum() {
            BenefitResult r = calculator.calculate(input("1000.00", "500.00", "9200.00"));

            assertThat(r.planPays()).isEqualByComparingTo("800.00");
            assertThat(r.amountOverAnnualMaximum()).isEqualByComparingTo("0.00");
            assertThat(r.cappedByAnnualMaximum()).isFalse();
            assertReconciles(r);
        }

        @Test
        @DisplayName("annual maximum already used up: plan pays zero")
        void maximumAlreadyExhausted() {
            BenefitResult r = calculator.calculate(input("1000.00", "500.00", "10000.00"));

            assertThat(r.planPays()).isEqualByComparingTo("0.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("1000.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("bad data with benefits paid above the maximum never produces a negative payment")
        void paidAboveMaximumIsClampedToZero() {
            BenefitResult r = calculator.calculate(input("1000.00", "500.00", "10500.00"));

            assertThat(r.planPays()).isEqualByComparingTo("0.00");
            assertReconciles(r);
        }

        @Test
        @DisplayName("deductible and annual maximum both apply to the same claim")
        void deductibleAndMaximumTogether() {
            // $200 deductible left, then $800 * 80% = $640, but only $300 of max remains
            BenefitResult r = calculator.calculate(input("1000.00", "300.00", "9700.00"));

            assertThat(r.deductibleApplied()).isEqualByComparingTo("200.00");
            assertThat(r.planPays()).isEqualByComparingTo("300.00");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("700.00");
            assertReconciles(r);
        }
    }

    @Nested
    @DisplayName("Rounding and precision")
    class Rounding {

        @Test
        @DisplayName("fractional cents round HALF_UP and the cents still add up")
        void fractionalCents() {
            // 123.57 * 0.80 = 98.856 -> 98.86
            BenefitResult r = calculator.calculate(input("123.57", "500.00", "0.00"));

            assertThat(r.planPays()).isEqualByComparingTo("98.86");
            assertThat(r.coinsuranceAmount()).isEqualByComparingTo("24.71");
            assertReconciles(r);
        }

        @Test
        @DisplayName("exact half cent rounds up (HALF_UP, not banker's rounding)")
        void halfCentRoundsUp() {
            // 10.30 * 0.75 = 7.725 -> 7.73 (HALF_EVEN would give 7.72)
            BenefitInput in = new BenefitInput(bd("10.30"), bd("0"), bd("0"), bd("0.75"), bd("10000"), bd("0"));
            BenefitResult r = calculator.calculate(in);

            assertThat(r.planPays()).isEqualByComparingTo("7.73");
            assertThat(r.memberResponsibility()).isEqualByComparingTo("2.57");
            assertReconciles(r);
        }

        @Test
        @DisplayName("results are always scale 2")
        void scaleIsTwo() {
            BenefitResult r = calculator.calculate(input("1000", "0", "0"));

            assertThat(r.planPays().scale()).isEqualTo(2);
            assertThat(r.memberResponsibility().scale()).isEqualTo(2);
            assertThat(r.deductibleApplied().scale()).isEqualTo(2);
        }

        @ParameterizedTest(name = "billed={0}, met={1}, paid={2}")
        @CsvSource({
                "0.01, 0, 0", "499.99, 0, 0", "500.01, 0, 0", "777.77, 123.45, 9876.54",
                "99999.99, 0, 0", "1.03, 500, 0", "12345.67, 499.99, 9999.99"
        })
        @DisplayName("every split reconciles to the billed amount")
        void alwaysReconciles(String billed, String met, String paid) {
            assertReconciles(calculator.calculate(input(billed, met, paid)));
        }
    }

    @Nested
    @DisplayName("Input validation")
    class Validation {

        @Test
        void rejectsNegativeBilledAmount() {
            assertThatThrownBy(() -> calculator.calculate(input("-1.00", "0", "0")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("billedAmount");
        }

        @Test
        void rejectsCoinsuranceAboveOne() {
            BenefitInput in = new BenefitInput(bd("100"), bd("0"), bd("0"), bd("1.01"), bd("1000"), bd("0"));
            assertThatThrownBy(() -> calculator.calculate(in))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("coinsuranceRate");
        }

        @Test
        void rejectsMissingValues() {
            BenefitInput in = new BenefitInput(null, bd("0"), bd("0"), bd("0.8"), bd("1000"), bd("0"));
            assertThatThrownBy(() -> calculator.calculate(in)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
