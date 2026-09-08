package com.moneykk.moneytown.analysis.ai.domain.repository;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    Optional<Portfolio> findByIdAndIsDeletedIsFalse(UUID id);

    Optional<Portfolio> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);


    // 벌크 Update라 updatedBy null , updatedAt만 명시
    @Modifying(clearAutomatically = true)
    @Query("""
        update Portfolio p
           set p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.FAILED,
               p.errorMessage = :message,
               p.completedAt = :now,
               p.updatedAt = :now
         where p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.PROCESSING
           and p.isDeleted = false
           and p.createdAt < :threshold
        """)
    int failStaleProcessing(@Param("message") String message,
                            @Param("now") Instant now,
                            @Param("threshold") Instant threshold);
}
