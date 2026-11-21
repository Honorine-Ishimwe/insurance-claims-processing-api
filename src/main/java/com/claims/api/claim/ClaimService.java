package com.claims.api.claim;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claims.api.audit.AuditAction;
import com.claims.api.audit.AuditService;
import com.claims.api.benefit.BenefitCalculator;
import com.claims.api.benefit.BenefitInput;
import com.claims.api.benefit.BenefitResult;
import com.claims.api.claim.dto.AdjudicationResponse;
import com.claims.api.claim.dto.ClaimResponse;
import com.claims.api.claim.dto.SubmitClaimRequest;
import com.claims.api.common.NotFoundException;
import com.claims.api.policy.Policy;
import com.claims.api.policy.PolicyRepository;
import com.claims.api.rules.ClaimRulesEngine;
import com.claims.api.rules.RulesEvaluation;
import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;

@Service
@Transactional
public class ClaimService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final AppUserRepository users;
    private final ClaimRulesEngine rulesEngine;
    private final BenefitCalculator calculator;
    private final AuditService audit;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, PolicyRepository policies, AppUserRepository users,
                        ClaimRulesEngine rulesEngine, BenefitCalculator calculator, AuditService audit, Clock clock) {
        this.claims = claims;
        this.policies = policies;
        this.users = users;
        this.rulesEngine = rulesEngine;
        this.calculator = calculator;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- member

    public ClaimResponse submit(String username, SubmitClaimRequest request) {
        AppUser member = user(username);
        Policy policy = policies.findByPolicyNumber(request.policyNumber())
                .filter(p -> p.getMember().getId().equals(member.getId()))
                // Same message whether the policy is missing or someone else's: don't leak existence.
                .orElseThrow(() -> new NotFoundException("Policy " + request.policyNumber() + " not found"));

        LocalDate today = LocalDate.now(clock);
        Claim claim = claims.save(new Claim(newClaimNumber(today), policy, request.providerName().trim(),
                request.serviceCode(), request.serviceDate(), today, request.billedAmount()));
        audit.recordAs(username, AuditAction.CLAIM_SUBMITTED, "CLAIM", claim.getId(),
                claim.getClaimNumber() + " policy=" + policy.getPolicyNumber()
                        + " serviceCode=" + claim.getServiceCode() + " billed=" + claim.getBilledAmount());
        return ClaimResponse.from(claim);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listForMember(String username, Pageable pageable) {
        return claims.findByPolicyMemberId(user(username).getId(), pageable).map(ClaimResponse::from);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getForMember(String username, Long claimId) {
        return claims.findByIdAndPolicyMemberId(claimId, user(username).getId())
                .map(ClaimResponse::from)
                .orElseThrow(() -> claimNotFound(claimId));
    }

    // -------------------------------------------------------------- adjuster

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listAll(ClaimStatus status, Pageable pageable) {
        Page<Claim> result = status == null
                ? claims.findAll(pageable)
                : claims.findByStatus(status, pageable);
        return result.map(ClaimResponse::from);
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long claimId) {
        return ClaimResponse.from(claim(claimId));
    }

    public ClaimResponse startReview(Long claimId) {
        Claim claim = claim(claimId);
        claim.transitionTo(ClaimStatus.IN_REVIEW);
        audit.record(AuditAction.CLAIM_REVIEW_STARTED, "CLAIM", claim.getId(), claim.getClaimNumber());
        return ClaimResponse.from(claim);
    }

    /**
     * Runs the rules engine. If every rule passes, calculates the benefit,
     * approves the claim and updates the policy's year-to-date accumulators
     * in the same transaction. Otherwise denies with every failure reason.
     */
    public AdjudicationResponse adjudicate(Long claimId) {
        Claim claim = claim(claimId);
        // Check before doing any work, so the error names the real problem.
        if (!claim.getStatus().canTransitionTo(ClaimStatus.APPROVED)) {
            throw new InvalidStatusTransitionException(claim.getStatus(), ClaimStatus.APPROVED);
        }

        RulesEvaluation evaluation = rulesEngine.evaluate(claim);
        if (evaluation.allPassed()) {
            Policy policy = claim.getPolicy();
            BenefitResult benefit = calculator.calculate(new BenefitInput(
                    claim.getBilledAmount(),
                    policy.getAnnualDeductible(),
                    policy.getDeductibleMet(),
                    policy.getCoinsuranceRate(),
                    policy.getAnnualMaximum(),
                    policy.getBenefitsPaidYtd()));
            claim.approve(benefit);
            policy.recordApprovedClaim(benefit.deductibleApplied(), benefit.planPays());
            audit.record(AuditAction.CLAIM_APPROVED, "CLAIM", claim.getId(),
                    claim.getClaimNumber() + " planPaid=" + benefit.planPays()
                            + " memberResponsibility=" + benefit.memberResponsibility());
        } else {
            claim.deny(evaluation.failureSummary());
            audit.record(AuditAction.CLAIM_DENIED, "CLAIM", claim.getId(),
                    claim.getClaimNumber() + " reasons=" + evaluation.failureSummary());
        }
        return new AdjudicationResponse(ClaimResponse.from(claim), evaluation.results());
    }

    public ClaimResponse markPaid(Long claimId) {
        Claim claim = claim(claimId);
        claim.transitionTo(ClaimStatus.PAID);
        audit.record(AuditAction.CLAIM_PAID, "CLAIM", claim.getId(),
                claim.getClaimNumber() + " planPaid=" + claim.getPlanPaid());
        return ClaimResponse.from(claim);
    }

    // --------------------------------------------------------------- helpers

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User " + username + " not found"));
    }

    private Claim claim(Long id) {
        return claims.findById(id).orElseThrow(() -> claimNotFound(id));
    }

    private static NotFoundException claimNotFound(Long id) {
        return new NotFoundException("Claim " + id + " not found");
    }

    private static String newClaimNumber(LocalDate today) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "CLM-" + today.getYear() + "-" + suffix;
    }
}
