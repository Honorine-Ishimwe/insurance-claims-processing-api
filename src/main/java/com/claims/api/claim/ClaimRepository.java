package com.claims.api.claim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Page<Claim> findByPolicyMemberId(Long memberId, Pageable pageable);

    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);

    Optional<Claim> findByIdAndPolicyMemberId(Long id, Long memberId);

    /**
     * A duplicate is another, non-denied claim for the same member, provider,
     * service code, service date and billed amount.
     */
    @Query("""
            select count(c) > 0 from Claim c
            where c.id <> :excludeId
              and c.policy.member.id = :memberId
              and lower(c.providerName) = lower(:providerName)
              and c.serviceCode = :serviceCode
              and c.serviceDate = :serviceDate
              and c.billedAmount = :billedAmount
              and c.status <> com.claims.api.claim.ClaimStatus.DENIED
            """)
    boolean existsDuplicate(@Param("excludeId") Long excludeId,
                            @Param("memberId") Long memberId,
                            @Param("providerName") String providerName,
                            @Param("serviceCode") String serviceCode,
                            @Param("serviceDate") LocalDate serviceDate,
                            @Param("billedAmount") BigDecimal billedAmount);
}
