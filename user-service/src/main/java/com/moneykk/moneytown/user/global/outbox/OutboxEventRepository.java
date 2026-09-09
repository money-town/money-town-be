package com.moneykk.moneytown.user.global.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT *
              FROM p_outbox_events
             WHERE event_status = 'PENDING'
               AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
             ORDER BY created_at ASC, event_id ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPublishableEventsForUpdate(
            @Param("batchSize") int batchSize
    );

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant getCurrentDatabaseTime();

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            UPDATE p_outbox_events
               SET event_status = 'PUBLISHED',
                   published_at = CURRENT_TIMESTAMP,
                   next_retry_at = NULL,
                   last_error = NULL
             WHERE event_id = :eventId
               AND event_status = 'PROCESSING'
               AND processing_started_at = :processingStartedAt
            """, nativeQuery = true)
    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("processingStartedAt") Instant processingStartedAt
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            UPDATE p_outbox_events
               SET retry_count = retry_count + 1,
                   last_error = :lastError,
                   event_status = CASE
                       WHEN retry_count + 1 >= :maxFailedAttempts THEN 'FAILED'
                       ELSE 'PENDING'
                   END,
                   next_retry_at = CASE
                       WHEN retry_count + 1 >= :maxFailedAttempts THEN NULL
                       ELSE CAST(:nextRetryAt AS TIMESTAMPTZ)
                   END
             WHERE event_id = :eventId
               AND event_status = 'PROCESSING'
               AND processing_started_at = :processingStartedAt
            """, nativeQuery = true)
    int markFailedAttempt(
            @Param("eventId") UUID eventId,
            @Param("processingStartedAt") Instant processingStartedAt,
            @Param("lastError") String lastError,
            @Param("maxFailedAttempts") int maxFailedAttempts,
            @Param("nextRetryAt") Instant nextRetryAt
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
            WITH expired_events AS (
                SELECT event_id
                  FROM p_outbox_events
                 WHERE event_status = 'PROCESSING'
                   AND processing_started_at <= :expiredBefore
                 ORDER BY processing_started_at ASC, event_id ASC
                 LIMIT :batchSize
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE p_outbox_events AS e
               SET retry_count = e.retry_count + 1,
                   last_error = '발행 처리 기한 초과로 복구됨',
                   event_status = CASE
                       WHEN e.retry_count + 1 >= :maxFailedAttempts THEN 'FAILED'
                       ELSE 'PENDING'
                   END,
                   next_retry_at = CASE
                       WHEN e.retry_count + 1 >= :maxFailedAttempts THEN NULL
                       ELSE CAST(:nextRetryAt AS TIMESTAMPTZ)
                   END
              FROM expired_events AS expired
             WHERE e.event_id = expired.event_id
            """, nativeQuery = true)
    int recoverExpiredProcessing(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("maxFailedAttempts") int maxFailedAttempts,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("batchSize") int batchSize
    );
}
