package com.moneykk.moneytown.offering.subscription.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_idempotency_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRequest {

    @Id
    @Column(
            name = "idempotency_request_id",
            nullable = false,
            updatable = false
    )
    private UUID idempotencyRequestId;

    /**
     * 요청 사용자 ID.
     */
    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private UUID userId;

    /**
     * 멱등성을 적용할 비즈니스 작업 종류.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "operation",
            nullable = false,
            length = 50,
            updatable = false
    )
    private IdempotencyOperation operation;

    /**
     * 클라이언트가 Idempotency-Key 헤더로 전달한 값.
     */
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String idempotencyKey;

    /**
     * 동일 멱등 키에 다른 요청 내용이 들어왔는지
     * 검증하기 위한 SHA-256 요청 해시.
     */
    @Column(
            name = "request_hash",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String requestHash;

    /**
     * 처리 결과로 생성되는 리소스 종류.
     *
     * 청약 생성 요청에서는 SUBSCRIPTION을 사용한다.
     */
    @Column(
            name = "resource_type",
            nullable = false,
            length = 50
    )
    private String resourceType;

    /**
     * 처리 완료 후 생성된 리소스 ID.
     *
     * 청약 생성이 완료되면 subscriptionId를 저장한다.
     * 처리 완료 전까지는 null일 수 있다.
     */
    @Column(name = "resource_id")
    private UUID resourceId;

    /**
     * 멱등 요청의 현재 처리 상태.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "idempotency_request_status",
            nullable = false,
            length = 20
    )
    private IdempotencyRequestStatus idempotencyRequestStatus;

    /**
     * 최초 처리의 HTTP 응답 코드.
     *
     * 동일 요청 재호출 시 기존 응답 상태를 복원할 때 사용한다.
     */
    @Column(name = "response_code")
    private Integer responseCode;

    /**
     * 멱등 요청이 최초 생성된 시각.
     */
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    /**
     * 요청 처리가 완료되거나 실패한 시각.
     */
    @Column(name = "completed_at")
    private Instant completedAt;
}