package com.claims.api.admin;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.claims.api.audit.AuditEventRepository;
import com.claims.api.audit.AuditEventResponse;
import com.claims.api.common.PageParams;
import com.claims.api.common.PageResponse;
import com.claims.api.policy.PolicyService;
import com.claims.api.policy.PolicyStatus;
import com.claims.api.policy.dto.CoveredServiceDtos.CoveredServiceRequest;
import com.claims.api.policy.dto.CoveredServiceDtos.CoveredServiceResponse;
import com.claims.api.policy.dto.CreatePolicyRequest;
import com.claims.api.policy.dto.PolicyResponse;
import com.claims.api.user.Role;
import com.claims.api.user.UserService;
import com.claims.api.user.dto.CreateUserRequest;
import com.claims.api.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Endpoints for administrators. Requires ROLE_ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "Manage users, policies, covered services and audit log (ADMIN role)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserService userService;
    private final PolicyService policyService;
    private final AuditEventRepository auditEvents;

    public AdminController(UserService userService, PolicyService policyService, AuditEventRepository auditEvents) {
        this.userService = userService;
        this.policyService = policyService;
        this.auditEvents = auditEvents;
    }

    // ------------------------------------------------------------------ users

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user account of any role (MEMBER, ADJUSTER, ADMIN)")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createByAdmin(request);
    }

    @GetMapping("/users")
    @Operation(summary = "List user accounts, optionally filtered by role")
    public PageResponse<UserResponse> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageParams.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.from(userService.list(role, pageable), u -> u);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user account by ID")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/users/{id}/enabled")
    @Operation(summary = "Enable or disable a user account",
            description = "Disabling takes effect immediately, revoking any active tokens for that user.")
    public UserResponse setUserEnabled(@PathVariable Long id,
                                       @Valid @RequestBody SetEnabledRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return userService.setEnabled(id, request.enabled(), jwt.getSubject());
    }

    // --------------------------------------------------------------- policies

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a new policy to an existing MEMBER account")
    public PolicyResponse createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.create(request);
    }

    @GetMapping("/policies")
    @Operation(summary = "List policies, optionally filtered by status")
    public PageResponse<PolicyResponse> listPolicies(
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from(policyService.list(status, pageable), p -> p);
    }

    @GetMapping("/policies/{policyNumber}")
    @Operation(summary = "Get a policy by policy number")
    public PolicyResponse getPolicy(@PathVariable String policyNumber) {
        return policyService.get(policyNumber);
    }

    @PatchMapping("/policies/{policyNumber}/status")
    @Operation(summary = "Change a policy's status (ACTIVE, LAPSED, CANCELLED)")
    public PolicyResponse changePolicyStatus(@PathVariable String policyNumber,
                                             @Valid @RequestBody ChangePolicyStatusRequest request) {
        return policyService.changeStatus(policyNumber, request.status());
    }

    // ------------------------------------------------------- covered services

    @GetMapping("/covered-services")
    @Operation(summary = "List the covered-service (CPT) catalog")
    public PageResponse<CoveredServiceResponse> listCoveredServices(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageParams.of(page, size, Sort.by(Sort.Direction.ASC, "serviceCode"));
        return PageResponse.from(policyService.listCoveredServices(pageable), s -> s);
    }

    @PutMapping("/covered-services/{serviceCode}")
    @Operation(summary = "Create or update a 5-digit covered service code")
    public CoveredServiceResponse saveCoveredService(@PathVariable String serviceCode,
                                                     @Valid @RequestBody CoveredServiceRequest request) {
        return policyService.saveCoveredService(serviceCode, request);
    }

    // -------------------------------------------------------------- audit log

    @GetMapping("/audit-events")
    @Operation(summary = "Query the append-only audit trail, newest first")
    public PageResponse<AuditEventResponse> listAuditEvents(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return PageResponse.from(auditEvents.search(entityType, entityId, actor, pageable), AuditEventResponse::from);
    }

    public record SetEnabledRequest(@NotNull(message = "enabled is required") Boolean enabled) {
    }

    public record ChangePolicyStatusRequest(@NotNull(message = "status is required") PolicyStatus status) {
    }
}
