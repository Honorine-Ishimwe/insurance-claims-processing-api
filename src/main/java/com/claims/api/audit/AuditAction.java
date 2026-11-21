package com.claims.api.audit;

public enum AuditAction {
    // Authentication
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    MEMBER_REGISTERED,

    // Claims
    CLAIM_SUBMITTED,
    CLAIM_REVIEW_STARTED,
    CLAIM_APPROVED,
    CLAIM_DENIED,
    CLAIM_PAID,

    // Administration
    USER_CREATED,
    USER_ENABLED,
    USER_DISABLED,
    POLICY_CREATED,
    POLICY_STATUS_CHANGED,
    COVERED_SERVICE_SAVED
}
