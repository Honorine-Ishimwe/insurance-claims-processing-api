package com.claims.api.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumberIgnoreCase(String policyNumber);

    List<Policy> findByMemberIdOrderByStartDateDesc(Long memberId);

    Page<Policy> findByStatus(PolicyStatus status, Pageable pageable);
}
