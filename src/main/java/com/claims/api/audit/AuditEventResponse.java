package com.claims.api.audit;

import java.time.Instant;

/** Read-only view of an audit trail row for administrators. */
public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        String actor,
        AuditAction action,
        String entityType,
        String entityId,
        String details,
        String requestId) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getActor(), e.getAction(),
                e.getEntityType(), e.getEntityId(), e.getDetails(), e.getRequestId());
    }
}
