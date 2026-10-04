package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
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
@Table(name = "p_subscription_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionRequest extends BaseUpdatableEntity {

    @Id
    @Column(name = "subscription_request_id", nullable = false)
    private UUID subscriptionRequestId;

    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private SubscriptionRequestStatus requestStatus;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static SubscriptionRequest create(
            UUID requestId,
            UUID offeringId,
            UUID userId,
            Long quantity,
            String idempotencyKey,
            String requestHash,
            String correlationId
    ) {
        if (requestId == null || offeringId == null || userId == null
                || quantity == null || quantity <= 0
                || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100
                || requestHash == null || requestHash.length() != 64
                || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 100) {
            throw new IllegalArgumentException("청약 접수 정보가 올바르지 않습니다.");
        }

        SubscriptionRequest request = new SubscriptionRequest();
        request.subscriptionRequestId = requestId;
        request.offeringId = offeringId;
        request.userId = userId;
        request.quantity = quantity;
        request.idempotencyKey = idempotencyKey;
        request.requestHash = requestHash;
        request.correlationId = correlationId;
        request.requestStatus = SubscriptionRequestStatus.QUEUED;
        return request;
    }

    public void startProcessing(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("처리 시작 시각은 필수입니다.");
        }
        if (requestStatus != SubscriptionRequestStatus.QUEUED
                && requestStatus != SubscriptionRequestStatus.PROCESSING) {
            throw new IllegalStateException("처리 가능한 청약 접수 상태가 아닙니다.");
        }
        this.requestStatus = SubscriptionRequestStatus.PROCESSING;
        this.processingStartedAt = startedAt;
    }

    public void complete(UUID completedSubscriptionId, Instant completedAt) {
        if (requestStatus != SubscriptionRequestStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 접수만 완료할 수 있습니다.");
        }
        if (completedSubscriptionId == null || completedAt == null) {
            throw new IllegalArgumentException("완료 청약 ID와 완료 시각은 필수입니다.");
        }
        this.requestStatus = SubscriptionRequestStatus.COMPLETED;
        this.subscriptionId = completedSubscriptionId;
        this.completedAt = completedAt;
        this.processingStartedAt = null;
    }

    public void reject(String code, Instant completedAt) {
        finishWithFailure(SubscriptionRequestStatus.REJECTED, code, completedAt);
    }

    public void fail(String code, Instant completedAt) {
        finishWithFailure(SubscriptionRequestStatus.FAILED, code, completedAt);
    }

    public void requeue() {
        if (requestStatus != SubscriptionRequestStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 접수만 복구할 수 있습니다.");
        }
        this.requestStatus = SubscriptionRequestStatus.QUEUED;
        this.processingStartedAt = null;
    }

    public boolean isTerminal() {
        return requestStatus == SubscriptionRequestStatus.COMPLETED
                || requestStatus == SubscriptionRequestStatus.REJECTED
                || requestStatus == SubscriptionRequestStatus.FAILED;
    }

    private void finishWithFailure(
            SubscriptionRequestStatus terminalStatus,
            String code,
            Instant finishedAt
    ) {
        if (requestStatus != SubscriptionRequestStatus.QUEUED
                && requestStatus != SubscriptionRequestStatus.PROCESSING) {
            throw new IllegalStateException(
                    "QUEUED 또는 PROCESSING 접수만 종료할 수 있습니다."
            );
        }
        if (code == null || code.isBlank() || code.length() > 100
                || finishedAt == null) {
            throw new IllegalArgumentException("실패 코드와 완료 시각이 올바르지 않습니다.");
        }
        this.requestStatus = terminalStatus;
        this.failureCode = code;
        this.completedAt = finishedAt;
        this.processingStartedAt = null;
    }
}
