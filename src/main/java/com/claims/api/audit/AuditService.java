package com.claims.api.audit;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claims.api.common.RequestIdFilter;

/**
 * Writes the audit trail. Each event is stored in the database (queryable by admins)
 * and also written to the dedicated {@code AUDIT} logger for log shipping.
 * <p>
 * Runs inside the caller's transaction when there is one, so an audit row is only
 * kept if the business change it describes was committed.
 */
@Service
public class AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final int MAX_DETAILS = 2000;

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /** Records an action performed by the currently authenticated user. */
    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, String details) {
        recordAs(currentActor(), action, entityType, entityId, details);
    }

    /** Records an action on behalf of a named actor (e.g. a login attempt before authentication). */
    @Transactional
    public void recordAs(String actor, AuditAction action, String entityType, Object entityId, String details) {
        String id = entityId == null ? null : String.valueOf(entityId);
        String safeActor = actor == null || actor.isBlank() ? "anonymous" : truncate(actor, 100);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        Instant now = clock.instant();
        events.save(new AuditEvent(now, safeActor, action, entityType, id, truncate(details, MAX_DETAILS), requestId));
        AUDIT_LOG.info("actor={} action={} entityType={} entityId={} details={}", safeActor, action, entityType, id,
                details);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return auth.getName();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
