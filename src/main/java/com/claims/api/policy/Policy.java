package com.claims.api.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.claims.api.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An insurance policy for one plan year ({@code startDate}..{@code endDate}).
 * Tracks the year-to-date accumulators (deductible met, benefits paid) that
 * the benefit calculation depends on. A new plan year is a new Policy row.
 */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private AppUser member;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status;

    @Column(name = "annual_deductible", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualDeductible;

    @Column(name = "deductible_met", nullable = false, precision = 12, scale = 2)
    private BigDecimal deductibleMet;

    /** Share of the post-deductible amount the plan pays, e.g. 0.8000 = 80%. */
    @Column(name = "coinsurance_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal coinsuranceRate;

    @Column(name = "annual_maximum", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualMaximum;

    @Column(name = "benefits_paid_ytd", nullable = false, precision = 12, scale = 2)
    private BigDecimal benefitsPaidYtd;

    protected Policy() {
        // for JPA
    }

    public Policy(String policyNumber, AppUser member, LocalDate startDate, LocalDate endDate,
                  PolicyStatus status, BigDecimal annualDeductible, BigDecimal deductibleMet,
                  BigDecimal coinsuranceRate, BigDecimal annualMaximum, BigDecimal benefitsPaidYtd) {
        this.policyNumber = policyNumber;
        this.member = member;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.annualDeductible = annualDeductible;
        this.deductibleMet = deductibleMet;
        this.coinsuranceRate = coinsuranceRate;
        this.annualMaximum = annualMaximum;
        this.benefitsPaidYtd = benefitsPaidYtd;
    }

    /** True if {@code date} falls inside the coverage period, both ends inclusive. */
    public boolean coversDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** Records an approved claim against the year-to-date accumulators. */
    public void recordApprovedClaim(BigDecimal deductibleApplied, BigDecimal planPaid) {
        this.deductibleMet = this.deductibleMet.add(deductibleApplied);
        this.benefitsPaidYtd = this.benefitsPaidYtd.add(planPaid);
    }

    /** E.g. ACTIVE -> CANCELLED. New claims on a non-active policy fail the eligibility rule. */
    public void changeStatus(PolicyStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public AppUser getMember() {
        return member;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public BigDecimal getAnnualDeductible() {
        return annualDeductible;
    }

    public BigDecimal getDeductibleMet() {
        return deductibleMet;
    }

    public BigDecimal getCoinsuranceRate() {
        return coinsuranceRate;
    }

    public BigDecimal getAnnualMaximum() {
        return annualMaximum;
    }

    public BigDecimal getBenefitsPaidYtd() {
        return benefitsPaidYtd;
    }
}
