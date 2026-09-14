package com.moneykk.moneytown.offering.global.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 발행 가능한 PENDING 이벤트를 생성 순서대로 조회하고 잠근다.
     *
     * 다른 발행기가 잠근 이벤트는 건너뛴다.
     * 호출한 트랜잭션 안에서 PROCESSING으로 변경해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT *
              FROM p_outbox_events
             WHERE event_status = 'PENDING'
               AND (
                   next_retry_at IS NULL
                   OR next_retry_at <= CURRENT_TIMESTAMP
               )
             ORDER BY created_at ASC, event_id ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPublishableEventsForUpdate(
            @Param("batchSize") int batchSize
    );

    /**
     * 현재 트랜잭션의 DB 기준 시각을 조회한다.
     */
    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant getCurrentDatabaseTime();

    /**
     * 현재 발행 시도가 성공한 경우에만 PUBLISHED로 변경한다.
     */
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

    /**
     * 현재 발행 시도의 실패를 기록한다.
     * 실패 누적 횟수가 한도에 도달하면 FAILED로 변경한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
        UPDATE p_outbox_events
           SET retry_count = retry_count + 1,
               last_error = :lastError,
               event_status =
                   CASE
                       WHEN retry_count + 1 >= :maxFailedAttempts
                       THEN 'FAILED'
                       ELSE 'PENDING'
                   END,
               next_retry_at =
                   CASE
                       WHEN retry_count + 1 >= :maxFailedAttempts
                       THEN NULL
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

    /**
     * 처리 기한이 지난 PROCESSING 이벤트를 복구한다.
     * 잠긴 행은 건너뛰고 배치 크기만큼 처리한다.
     */
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
               last_error = '발행 처리 기한 초과로 복구됨. Kafka 전송 결과는 미확정',
               event_status =
                   CASE
                       WHEN e.retry_count + 1 >= :maxFailedAttempts
                       THEN 'FAILED'
                       ELSE 'PENDING'
                   END,
               next_retry_at =
                   CASE
                       WHEN e.retry_count + 1 >= :maxFailedAttempts
                       THEN NULL
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

    /*
     *
     * 영구 실패 상태인 이벤트 한 건을 다시 발행 대기 상태로 전환한다.
     *
     * retry_count를 0으로 초기화하여 관리자가 재처리를 요청한 이후
     * 설정된 최대 재시도 횟수만큼 다시 시도할 수 있도록 한다.
     *
     * 기존 last_error는 운영자가 이전 실패 원인을 확인할 수 있도록
     * 재발행에 성공하거나 새로운 실패가 발생할 때까지 유지한다.
     *
     * @return 상태가 변경된 행 수
     *         1: FAILED 이벤트를 PENDING으로 변경
     *         0: 이벤트가 없거나 현재 상태가 FAILED가 아님
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE p_outbox_events
           SET event_status = 'PENDING',
               retry_count = 0,
               processing_started_at = NULL,
               next_retry_at = CURRENT_TIMESTAMP,
               published_at = NULL
         WHERE event_id = :eventId
           AND event_status = 'FAILED'
        """, nativeQuery = true)
    int requeueFailedEvent(
            @Param("eventId") UUID eventId
    );

    /**
     * 현재 DB에 남아 있는 특정 상태의 Outbox 이벤트 수를 조회한다.
     *
     * PROCESSING 및 FAILED 이벤트 적체 상태를
     * Micrometer Gauge로 제공할 때 사용한다.
     */
    long countByEventStatus(OutboxEventStatus eventStatus);

}
