package com.claims.api.claim;

/** Thrown when a claim is asked to move to a status its current status does not allow. */
public class InvalidStatusTransitionException extends RuntimeException {

    private final ClaimStatus from;
    private final ClaimStatus to;

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move claim from " + from + " to " + to
                + (from.isTerminal() ? " (" + from + " is a final status)" : "; allowed: " + from.allowedNext()));
        this.from = from;
        this.to = to;
    }

    public ClaimStatus getFrom() {
        return from;
    }

    public ClaimStatus getTo() {
        return to;
    }
}
