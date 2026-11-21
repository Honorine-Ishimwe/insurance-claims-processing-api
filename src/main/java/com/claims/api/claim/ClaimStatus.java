package com.claims.api.claim;

import java.util.Set;

/**
 * Lifecycle of a claim. Allowed transitions are defined in one place so the
 * business rule is easy to read, test, and change.
 *
 * <pre>
 * SUBMITTED -> IN_REVIEW -> APPROVED -> PAID
 *                        \-> DENIED
 * </pre>
 *
 * DENIED and PAID are terminal.
 */
public enum ClaimStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    DENIED,
    PAID;

    public Set<ClaimStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> Set.of(IN_REVIEW);
            case IN_REVIEW -> Set.of(APPROVED, DENIED);
            case APPROVED -> Set.of(PAID);
            case DENIED, PAID -> Set.of();
        };
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
