package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRequestRepository
        extends JpaRepository<IdempotencyRequest, UUID> {

    /**
     * 동일 사용자의 동일 작업에 대해
     * 같은 Idempotency-Key로 처리된 요청을 조회한다.
     */
    Optional<IdempotencyRequest> findByUserIdAndOperationAndIdempotencyKey(
            UUID userId,
            IdempotencyOperation operation,
            String idempotencyKey
    );

    /**
     * 신규 멱등 요청을 선점한다.
     *
     * DB의 UNIQUE(user_id, operation, idempotency_key)를 이용해
     * 동일 요청이 이미 존재하는 경우 INSERT하지 않는다.
     *
     * @return 1: 신규 요청 선점 성공, 0: 동일 키 요청이 이미 존재
     */
    @Modifying
    @Query(value = """
            INSERT INTO p_idempotency_requests (
                idempotency_request_id,
                user_id,
                operation,
                idempotency_key,
                request_hash,
                resource_type,
                idempotency_request_status,
                created_at
            )
            VALUES (
                :idempotencyRequestId,
                :userId,
                :operation,
                :idempotencyKey,
                :requestHash,
                :resourceType,
                'PROCESSING',
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, operation, idempotency_key)
            DO NOTHING
            """, nativeQuery = true)
    int tryInsert(
            @Param("idempotencyRequestId") UUID idempotencyRequestId,
            @Param("userId") UUID userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("resourceType") String resourceType
    );

    /**
     * 멱등 요청을 정상 처리 완료 상태로 변경한다.
     *
     * 청약 생성 요청에서는 resourceId에 subscriptionId를 저장한다.
     */
    @Modifying
    @Query(value = """
            UPDATE p_idempotency_requests
               SET resource_id = :resourceId,
                   idempotency_request_status = 'COMPLETED',
                   response_code = :responseCode,
                   completed_at = CURRENT_TIMESTAMP
             WHERE user_id = :userId
               AND operation = :operation
               AND idempotency_key = :idempotencyKey
               AND idempotency_request_status = 'PROCESSING'
            """, nativeQuery = true)
    int complete(
            @Param("userId") UUID userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("resourceId") UUID resourceId,
            @Param("responseCode") Integer responseCode
    );

    /**
     * 멱등 요청 처리 실패 상태를 기록한다.
     *
     * 실제 실패 정책 확정 후 사용 여부를 재검토한다.
     */
    @Modifying
    @Query(value = """
            UPDATE p_idempotency_requests
               SET idempotency_request_status = 'FAILED',
                   response_code = :responseCode,
                   completed_at = CURRENT_TIMESTAMP
             WHERE user_id = :userId
               AND operation = :operation
               AND idempotency_key = :idempotencyKey
               AND idempotency_request_status = 'PROCESSING'
            """, nativeQuery = true)
    int fail(
            @Param("userId") UUID userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("responseCode") Integer responseCode
    );
}