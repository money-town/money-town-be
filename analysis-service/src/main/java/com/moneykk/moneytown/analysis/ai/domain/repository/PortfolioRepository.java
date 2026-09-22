package com.moneykk.moneytown.analysis.ai.domain.repository;

import com.moneykk.moneytown.analysis.ai.domain.AiStatus;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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
           and p.updatedAt < :threshold
        """)
    int failStaleProcessing(@Param("message") String message,
                            @Param("now") Instant now,
                            @Param("threshold") Instant threshold);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Portfolio p
         set p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.COMPLETED,
             p.response = :json,
             p.processingTime = :ms,
             p.completedAt = :now,
             p.updatedAt = :now
       where p.id = :id
         and p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.PROCESSING
         and p.isDeleted = false
    """)
    int completeIfProcessing(@Param("id") UUID id, @Param("json") String json,
                             @Param("ms") long ms, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
    update Portfolio p
       set p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.FAILED,
           p.errorMessage = :msg,
           p.processingTime = :ms,
           p.completedAt = :now,
           p.updatedAt = :now
     where p.id = :id
       and p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.PROCESSING
       and p.isDeleted = false
    """)
    int failIfProcessing(@Param("id") UUID id, @Param("msg") String msg,
                         @Param("ms") long ms, @Param("now") Instant now);

    @Query(value = """
    select ai_portfolio_id
      from p_ai_portfolios
     where status = 'PENDING'
       and is_deleted = false
     order by created_at asc
     limit :limit
       for update skip locked
    """, nativeQuery = true)
    List<UUID> findPendingIdsForUpdateSkipLocked(@Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query("""
    update Portfolio p
       set p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.PROCESSING,
           p.updatedAt = :now
     where p.id in :ids
       and p.status = com.moneykk.moneytown.analysis.ai.domain.AiStatus.PENDING
    """)
    int markProcessing(@Param("ids") List<UUID> ids, @Param("now") Instant now);

    // findByUserIdAndStatusAndIsDeleted → PENDING+PROCESSING 둘 다 보도록 확장
    Optional<Portfolio> findFirstByUserIdAndStatusInAndIsDeleted(UUID userId, List<AiStatus> statuses, boolean isDeleted);

    // countByStatusAndIsDeleted → 두 상태 합산용으로 확장
    int countByStatusInAndIsDeleted(List<AiStatus> statuses, boolean isDeleted);
}
