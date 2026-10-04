package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRequestRepository
        extends JpaRepository<SubscriptionRequest, UUID> {

    Optional<SubscriptionRequest> findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
        INSERT INTO p_subscription_requests (
            subscription_request_id, offering_id, user_id, quantity,
            idempotency_key, request_hash, correlation_id, request_status,
            created_by, updated_by
        ) VALUES (
            :requestId, :offeringId, :userId, :quantity,
            :idempotencyKey, :requestHash, :correlationId, 'QUEUED',
            :userId, :userId
        )
        ON CONFLICT (user_id, idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("requestId") UUID requestId,
            @Param("offeringId") UUID offeringId,
            @Param("userId") UUID userId,
            @Param("quantity") Long quantity,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("correlationId") String correlationId
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM SubscriptionRequest r
         WHERE r.subscriptionRequestId = :requestId
        """)
    Optional<SubscriptionRequest> findByIdForUpdate(
            @Param("requestId") UUID requestId
    );

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
        SELECT * FROM p_subscription_requests
         WHERE request_status = 'PROCESSING'
           AND processing_started_at <= :stuckBefore
         ORDER BY processing_started_at, subscription_request_id
         LIMIT :batchSize
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<SubscriptionRequest> findStuckProcessingForUpdate(
            @Param("stuckBefore") Instant stuckBefore,
            @Param("batchSize") int batchSize
    );

    long countByRequestStatus(SubscriptionRequestStatus status);
}
