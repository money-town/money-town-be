package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    /**
     * 현재 트랜잭션의 DB 기준 시각을 조회한다.
     *
     * 애플리케이션 서버와 DB 서버의 시각 차이로 인해
     * 정상 처리 중인 요청을 오래된 요청으로 오인하지 않도록 사용한다.
     */
    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant getCurrentDatabaseTime();

    /**
     * 처리 제한 시간을 초과한 PROCESSING 멱등 요청을 FAILED로 복구한다.
     *
     * 여러 인스턴스에서 동시에 실행되더라도
     * FOR UPDATE SKIP LOCKED를 사용하여 같은 요청을 중복 처리하지 않는다.
     *
     * resource_id가 없는 요청만 대상으로 하여
     * 아직 처리 결과가 확정되지 않은 요청만 복구한다.
     *
     * @return FAILED로 변경된 요청 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(value = """
        WITH expired_requests AS (
            SELECT idempotency_request_id
              FROM p_idempotency_requests
             WHERE idempotency_request_status = 'PROCESSING'
               AND resource_id IS NULL
               AND created_at <= :expiredBefore
             ORDER BY created_at ASC, idempotency_request_id ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
        )
        UPDATE p_idempotency_requests AS request
           SET idempotency_request_status = 'FAILED',
               response_code = :responseCode,
               completed_at = CURRENT_TIMESTAMP
          FROM expired_requests AS expired
         WHERE request.idempotency_request_id =
               expired.idempotency_request_id
        """, nativeQuery = true)
    int recoverExpiredProcessingRequests(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("responseCode") int responseCode,
            @Param("batchSize") int batchSize
    );
}