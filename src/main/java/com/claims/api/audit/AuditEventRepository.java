package com.claims.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Every filter is optional: pass null to ignore it. */
    @Query("""
            select e from AuditEvent e
            where (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
              and (:actor is null or e.actor = :actor)
            """)
    Page<AuditEvent> search(@Param("entityType") String entityType,
                            @Param("entityId") String entityId,
                            @Param("actor") String actor,
                            Pageable pageable);
}
