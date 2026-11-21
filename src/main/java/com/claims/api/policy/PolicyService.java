package com.claims.api.policy;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claims.api.audit.AuditAction;
import com.claims.api.audit.AuditService;
import com.claims.api.common.BadRequestException;
import com.claims.api.common.ConflictException;
import com.claims.api.common.NotFoundException;
import com.claims.api.policy.dto.CoveredServiceDtos.CoveredServiceRequest;
import com.claims.api.policy.dto.CoveredServiceDtos.CoveredServiceResponse;
import com.claims.api.policy.dto.CreatePolicyRequest;
import com.claims.api.policy.dto.PolicyResponse;
import com.claims.api.user.AppUser;
import com.claims.api.user.AppUserRepository;
import com.claims.api.user.Role;

/** Policy administration, the member's own policy view, and the covered-service catalog. */
@Service
@Transactional
public class PolicyService {

    private final PolicyRepository policies;
    private final CoveredServiceRepository coveredServices;
    private final AppUserRepository users;
    private final AuditService audit;

    public PolicyService(PolicyRepository policies, CoveredServiceRepository coveredServices,
                         AppUserRepository users, AuditService audit) {
        this.policies = policies;
        this.coveredServices = coveredServices;
        this.users = users;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ admin

    public PolicyResponse create(CreatePolicyRequest request) {
        AppUser member = users.findByUsername(request.memberUsername().trim())
                .orElseThrow(() -> new NotFoundException("User " + request.memberUsername() + " not found"));
        if (member.getRole() != Role.MEMBER) {
            throw new BadRequestException("Policies can only be issued to MEMBER accounts");
        }
        String number = request.policyNumber().trim().toUpperCase(java.util.Locale.ROOT);
        if (policies.existsByPolicyNumberIgnoreCase(number)) {
            throw new ConflictException("Policy number " + number + " already exists");
        }
        Policy policy = policies.save(new Policy(number, member, request.startDate(), request.endDate(),
                PolicyStatus.ACTIVE, request.annualDeductible(), BigDecimal.ZERO.setScale(2),
                request.coinsuranceRate(), request.annualMaximum(), BigDecimal.ZERO.setScale(2)));
        audit.record(AuditAction.POLICY_CREATED, "POLICY", policy.getId(),
                "Issued " + number + " to '" + member.getUsername() + "' for " + request.startDate()
                        + ".." + request.endDate());
        return PolicyResponse.from(policy);
    }

    @Transactional(readOnly = true)
    public Page<PolicyResponse> list(PolicyStatus status, Pageable pageable) {
        Page<Policy> page = status == null ? policies.findAll(pageable) : policies.findByStatus(status, pageable);
        return page.map(PolicyResponse::from);
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(String policyNumber) {
        return PolicyResponse.from(find(policyNumber));
    }

    public PolicyResponse changeStatus(String policyNumber, PolicyStatus status) {
        Policy policy = find(policyNumber);
        PolicyStatus previous = policy.getStatus();
        if (previous != status) {
            policy.changeStatus(status);
            audit.record(AuditAction.POLICY_STATUS_CHANGED, "POLICY", policy.getId(),
                    policy.getPolicyNumber() + ": " + previous + " -> " + status);
        }
        return PolicyResponse.from(policy);
    }

    // ----------------------------------------------------------------- member

    @Transactional(readOnly = true)
    public List<PolicyResponse> listForMember(String username) {
        AppUser member = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User " + username + " not found"));
        return policies.findByMemberIdOrderByStartDateDesc(member.getId()).stream()
                .map(PolicyResponse::from).toList();
    }

    // ---------------------------------------------------------------- catalog

    @Transactional(readOnly = true)
    public Page<CoveredServiceResponse> listCoveredServices(Pageable pageable) {
        return coveredServices.findAll(pageable).map(CoveredServiceResponse::from);
    }

    /** Create or update a catalog entry. Affects how future adjudications treat this code. */
    public CoveredServiceResponse saveCoveredService(String serviceCode, CoveredServiceRequest request) {
        if (!serviceCode.matches("\\d{5}")) {
            throw new BadRequestException("serviceCode must be a 5-digit code");
        }
        CoveredService service = coveredServices.findById(serviceCode)
                .map(existing -> {
                    existing.update(request.description().trim(), request.covered());
                    return existing;
                })
                .orElseGet(() -> coveredServices.save(
                        new CoveredService(serviceCode, request.description().trim(), request.covered())));
        audit.record(AuditAction.COVERED_SERVICE_SAVED, "COVERED_SERVICE", serviceCode,
                "covered=" + request.covered() + ", description=" + request.description().trim());
        return CoveredServiceResponse.from(service);
    }

    private Policy find(String policyNumber) {
        return policies.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new NotFoundException("Policy " + policyNumber + " not found"));
    }
}
