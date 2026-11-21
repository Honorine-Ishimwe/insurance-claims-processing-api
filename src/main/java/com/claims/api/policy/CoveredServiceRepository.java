package com.claims.api.policy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoveredServiceRepository extends JpaRepository<CoveredService, String> {
}
