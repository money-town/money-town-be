package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.HoldingServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 관리자의 청약 재처리 요청을 조율한다.
 *
 * Wallet 및 Holding 상태 조회는 DB 쓰기 트랜잭션 밖에서 수행하고,
 * 청약 상태 변경과 Outbox 저장은
 * SubscriptionRetryTransactionService에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionRetryCommandService {

    private static final String SUBSCRIPTION_RESOURCE_TYPE =
            "SUBSCRIPTION";

    private static final String SYSTEM_ROLE =
            "SYSTEM";

    private static final String RESERVATION_EXPIRED_FAILURE_CODE =
            "RESERVATION_EXPIRED";

    private final SubscriptionRepository subscriptionRepository;

    private final IdempotencyRequestRepository
            idempotencyRequestRepository;

    private final SubscriptionIdempotencyService
            subscriptionIdempotencyService;

    private final SubscriptionRetryTransactionService
            subscriptionRetryTransactionService;

    private final SubscriptionRequestHasher
            subscriptionRequestHasher;

    private final WalletServiceClient walletServiceClient;
    private final HoldingServiceClient holdingServiceClient;

    /**
     * 관리자 청약 재처리 요청을 수행한다.
     */
    public SubscriptionRetryResult retry(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey,
            String correlationId
    ) {
        validateInput(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        String normalizedCorrelationId =
                normalizeCorrelationId(correlationId);

        String requestHash =
                subscriptionRequestHasher.hashRetry(
                        subscriptionId
                );

        IdempotencyOperation operation =
                IdempotencyOperation.RETRY_SUBSCRIPTION;

        UUID idempotencyRequestId =
                UUID.randomUUID();

        int inserted =
                subscriptionIdempotencyService.tryBegin(
                        idempotencyRequestId,
                        adminId,
                        operation.name(),
                        idempotencyKey,
                        requestHash,
                        SUBSCRIPTION_RESOURCE_TYPE
                );

        /*
         * 같은 Idempotency-Key가 이미 존재하면
         * 기존 요청의 상태에 따라 동일 결과를 반환하거나 오류를 발생시킨다.
         */
        if (inserted == 0) {
            return handleExistingRequest(
                    adminId,
                    operation,
                    idempotencyKey,
                    requestHash
            );
        }

        try {
            Subscription subscription =
                    findSubscription(subscriptionId);

            validateRetryCandidate(subscription);

            /*
             * 외부 HTTP 통신은 DB 쓰기 트랜잭션 밖에서 수행한다.
             */
            WalletHoldStatusResponse walletStatus =
                    getWalletStatus(subscriptionId);

            HoldingSubscriptionStatusResponse holdingStatus =
                    getHoldingStatus(subscriptionId);

            SubscriptionRetryResponse response =
                    subscriptionRetryTransactionService.retry(
                            subscriptionId,
                            adminId,
                            idempotencyKey,
                            normalizedCorrelationId,
                            walletStatus,
                            holdingStatus
                    );

            return SubscriptionRetryResult.created(
                    response
            );

        } catch (BusinessException e) {
            subscriptionIdempotencyService.fail(
                    adminId,
                    operation.name(),
                    idempotencyKey,
                    e.getErrorCode().getStatus().value()
            );

            throw e;

        } catch (RuntimeException e) {
            subscriptionIdempotencyService.fail(
                    adminId,
                    operation.name(),
                    idempotencyKey,
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            );

            throw e;
        }
    }

    /**
     * 현재 청약이 관리자 재처리 대상인지 확인한다.
     *
     * 재처리 대상:
     * 1. MANUAL_REVIEW
     * 2. CONFIRMED + Holding 배정 FAILED
     */
    private void validateRetryCandidate(
            Subscription subscription
    ) {
        boolean manualReview =
                subscription.getSubscriptionStatus()
                        == SubscriptionStatus.MANUAL_REVIEW;

        boolean confirmedHoldingFailure =
                subscription.getSubscriptionStatus()
                        == SubscriptionStatus.CONFIRMED
                        && subscription.getHoldingAllocationStatus()
                        == HoldingAllocationStatus.FAILED;

        if (!manualReview && !confirmedHoldingFailure) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        /*
         * 이미 확보 수량이 복원됐다면
         * 정상 청약 방향으로 다시 진행할 수 없다.
         */
        if (!subscription.isQuantityReserved()) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        /*
         * 공모 중단 또는 모집 미달 취소로 인한 상태는
         * 재처리 API가 아닌 보상 API에서 처리한다.
         */
        if (subscription.getCancellationType() != null) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        /*
         * 예약 만료 청약도 정상 처리 방향으로 되돌리지 않고
         * 관리자 보상 API에서 처리한다.
         */
        if (RESERVATION_EXPIRED_FAILURE_CODE.equals(
                subscription.getFailureCode()
        )) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }
    }

    /**
     * Wallet Hold 상태를 조회한다.
     *
     * Wallet의 404는 장애가 아니라
     * 아직 Hold가 생성되지 않은 상태로 처리한다.
     */
    private WalletHoldStatusResponse getWalletStatus(
            UUID subscriptionId
    ) {
        try {
            ApiResponse<WalletHoldStatusResponse> response =
                    walletServiceClient.getWalletHoldStatus(
                            subscriptionId
                    );

            WalletHoldStatusResponse status =
                    response != null && response.success()
                            ? response.data()
                            : null;

            if (status == null
                    || !subscriptionId.equals(
                    status.subscriptionId()
            )
                    || status.amount() == null
                    || status.status() == null
                    || status.updatedAt() == null) {
                throw new BusinessException(
                        SubscriptionErrorCode
                                .EXTERNAL_RESPONSE_INVALID
                );
            }

            return status;

        } catch (FeignException.NotFound e) {
            /*
             * Wallet 내부 API 계약:
             * 해당 subscriptionId의 Hold가 없으면 404를 반환한다.
             */
            return null;

        } catch (FeignException e) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .WALLET_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * Holding의 청약별 지분 처리 상태를 조회한다.
     */
    private HoldingSubscriptionStatusResponse getHoldingStatus(
            UUID subscriptionId
    ) {
        try {
            ApiResponse<HoldingSubscriptionStatusResponse> response =
                    holdingServiceClient
                            .getHoldingSubscriptionStatus(
                                    SYSTEM_ROLE,
                                    subscriptionId
                            );

            HoldingSubscriptionStatusResponse status =
                    response != null && response.success()
                            ? response.data()
                            : null;

            if (status == null
                    || !subscriptionId.equals(
                    status.subscriptionId()
            )
                    || status.lastProcessedAt() == null
                    && (
                    status.allocationProcessed()
                            || status.revocationProcessed()
                            || status.allocationBlocked()
            )) {
                throw new BusinessException(
                        SubscriptionErrorCode
                                .EXTERNAL_RESPONSE_INVALID
                );
            }

            return status;

        } catch (FeignException e) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .HOLDING_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * 동일 Idempotency-Key 요청을 처리한다.
     */
    private SubscriptionRetryResult handleExistingRequest(
            UUID adminId,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRequest existing =
                idempotencyRequestRepository
                        .findByUserIdAndOperationAndIdempotencyKey(
                                adminId,
                                operation,
                                idempotencyKey
                        )
                        .orElseThrow(() -> new BusinessException(
                                SubscriptionErrorCode
                                        .IDEMPOTENCY_REQUEST_STATE_INVALID
                        ));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .IDEMPOTENCY_KEY_CONFLICT
            );
        }

        IdempotencyRequestStatus status =
                existing.getIdempotencyRequestStatus();

        if (status == IdempotencyRequestStatus.COMPLETED) {
            return SubscriptionRetryResult.replayed(
                    getCompletedSubscription(existing)
            );
        }

        if (status == IdempotencyRequestStatus.PROCESSING) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .IDEMPOTENCY_REQUEST_PROCESSING
            );
        }

        if (status == IdempotencyRequestStatus.FAILED) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .IDEMPOTENCY_REQUEST_FAILED
            );
        }

        throw new BusinessException(
                SubscriptionErrorCode
                        .IDEMPOTENCY_REQUEST_STATE_INVALID
        );
    }

    /**
     * 완료된 멱등 요청에 저장된 청약 결과를 조회한다.
     */
    private SubscriptionRetryResponse getCompletedSubscription(
            IdempotencyRequest idempotencyRequest
    ) {
        UUID resourceId =
                idempotencyRequest.getResourceId();

        if (resourceId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .IDEMPOTENCY_REQUEST_STATE_INVALID
            );
        }

        Subscription subscription =
                findSubscription(resourceId);

        return SubscriptionRetryResponse.from(
                subscription
        );
    }

    private Subscription findSubscription(
            UUID subscriptionId
    ) {
        return subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                )
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode
                                .SUBSCRIPTION_NOT_FOUND
                ));
    }

    private void validateInput(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        if (subscriptionId == null
                || adminId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .INVALID_IDEMPOTENCY_KEY
            );
        }
    }

    /**
     * Correlation-ID가 전달되지 않았다면 서버에서 생성한다.
     */
    private String normalizeCorrelationId(
            String correlationId
    ) {
        if (correlationId == null
                || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return correlationId;
    }
}