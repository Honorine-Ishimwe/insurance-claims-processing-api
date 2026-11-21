package com.claims.api.user;

public enum Role {
    /** Insured person: submits claims and views their own claims and policies. */
    MEMBER,
    /** Claims staff: reviews, adjudicates and pays claims. */
    ADJUSTER,
    /** Administrator: manages users, policies, the service catalog and reads the audit log. */
    ADMIN
}
