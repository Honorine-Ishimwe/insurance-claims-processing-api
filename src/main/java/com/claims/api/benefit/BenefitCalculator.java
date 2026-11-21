package com.claims.api.benefit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Calculates how a claim is split between the plan and the member.
 *
 * <ol>
 *   <li><b>Deductible</b> – the member pays first, until the annual deductible is met.</li>
 *   <li><b>Coinsurance</b> – of what is left, the plan pays {@code coinsuranceRate} (e.g. 80%)
 *       and the member pays the rest.</li>
 *   <li><b>Annual maximum</b> – the plan's share is capped at whatever remains of the annual maximum;
 *       anything above the cap becomes the member's responsibility.</li>
 * </ol>
 *
 * All money is {@link BigDecimal} at scale 2. Rounding happens once, on the plan share,
 * using HALF_UP; every other figure is derived by subtraction so cents always add up.
 * The class is stateless and has no dependencies, so it is safe to share and easy to test.
 */
@Component
public class BenefitCalculator {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public BenefitResult calculate(BenefitInput in) {
        validate(in);

        BigDecimal billed = money(in.billedAmount());

        // 1. Deductible
        BigDecimal remainingDeductible = nonNegative(in.annualDeductible().subtract(in.deductibleMet()));
        BigDecimal deductibleApplied = money(billed.min(remainingDeductible));
        BigDecimal afterDeductible = billed.subtract(deductibleApplied);

        // 2. Coinsurance
        BigDecimal planShare = money(afterDeductible.multiply(in.coinsuranceRate()));
        BigDecimal coinsurance = afterDeductible.subtract(planShare);

        // 3. Annual maximum
        BigDecimal remainingMaximum = nonNegative(in.annualMaximum().subtract(in.benefitsPaidYtd()));
        BigDecimal planPays = money(planShare.min(remainingMaximum));
        BigDecimal overMaximum = planShare.subtract(planPays);

        // 4. Member owes everything the plan does not pay
        BigDecimal memberResponsibility = billed.subtract(planPays);

        return new BenefitResult(billed, deductibleApplied, money(coinsurance), money(overMaximum),
                planPays, money(memberResponsibility));
    }

    private static void validate(BenefitInput in) {
        Objects.requireNonNull(in, "input");
        requireNonNegative(in.billedAmount(), "billedAmount");
        requireNonNegative(in.annualDeductible(), "annualDeductible");
        requireNonNegative(in.deductibleMet(), "deductibleMet");
        requireNonNegative(in.annualMaximum(), "annualMaximum");
        requireNonNegative(in.benefitsPaidYtd(), "benefitsPaidYtd");
        requireNonNegative(in.coinsuranceRate(), "coinsuranceRate");
        if (in.coinsuranceRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("coinsuranceRate must be between 0 and 1");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.max(BigDecimal.ZERO);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }
}
