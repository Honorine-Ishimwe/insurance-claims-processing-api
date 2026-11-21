package com.claims.api.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.claims.api.benefit.BenefitResult;
import com.claims.api.policy.Policy;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A claim for one service on one date. Status changes go through
 * {@link #transitionTo(ClaimStatus)} so invalid moves are impossible from
 * anywhere in the code base, not just from the service layer.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "submission_date", nullable = false)
    private LocalDate submissionDate;

    @Column(name = "billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "denial_reason", length = 1000)
    private String denialReason;

    // --- Adjudication results (populated on approval) ---

    @Column(name = "deductible_applied", precision = 12, scale = 2)
    private BigDecimal deductibleApplied;

    @Column(name = "coinsurance_amount", precision = 12, scale = 2)
    private BigDecimal coinsuranceAmount;

    @Column(name = "plan_paid", precision = 12, scale = 2)
    private BigDecimal planPaid;

    @Column(name = "member_responsibility", precision = 12, scale = 2)
    private BigDecimal memberResponsibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Claim() {
        // for JPA
    }

    public Claim(String claimNumber, Policy policy, String providerName, String serviceCode,
                 LocalDate serviceDate, LocalDate submissionDate, BigDecimal billedAmount) {
        this.claimNumber = claimNumber;
        this.policy = policy;
        this.providerName = providerName;
        this.serviceCode = serviceCode;
        this.serviceDate = serviceDate;
        this.submissionDate = submissionDate;
        this.billedAmount = billedAmount;
        this.status = ClaimStatus.SUBMITTED;
    }

    /** The only way to change status. Throws if the move is not allowed. */
    public void transitionTo(ClaimStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(status, next);
        }
        this.status = next;
    }

    public void approve(BenefitResult result) {
        transitionTo(ClaimStatus.APPROVED);
        this.deductibleApplied = result.deductibleApplied();
        this.coinsuranceAmount = result.coinsuranceAmount();
        this.planPaid = result.planPays();
        this.memberResponsibility = result.memberResponsibility();
    }

    public void deny(String reason) {
        transitionTo(ClaimStatus.DENIED);
        this.denialReason = reason;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Policy getPolicy() {
        return policy;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public LocalDate getSubmissionDate() {
        return submissionDate;
    }

    public BigDecimal getBilledAmount() {
        return billedAmount;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getDenialReason() {
        return denialReason;
    }

    public BigDecimal getDeductibleApplied() {
        return deductibleApplied;
    }

    public BigDecimal getCoinsuranceAmount() {
        return coinsuranceAmount;
    }

    public BigDecimal getPlanPaid() {
        return planPaid;
    }

    public BigDecimal getMemberResponsibility() {
        return memberResponsibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
