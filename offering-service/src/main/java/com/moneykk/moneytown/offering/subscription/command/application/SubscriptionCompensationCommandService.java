package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResult;
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
 * 관리자의 청약 보상 요청을 조율한다.
 *
 * 외부 Wallet/Holding 조회는 DB 쓰기 트랜잭션 밖에서 수행하고,
 * 상태 변경과 Outbox 저장은 Transaction Service에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionCompensationCommandService {

    private static final String SUBSCRIPTION_RESOURCE_TYPE =
            "SUBSCRIPTION";

    private static final String SYSTEM_ROLE = "SYSTEM";

    private final SubscriptionRepository subscriptionRepository;
    private final IdempotencyRequestRepository
            idempotencyRequestRepository;

    private final SubscriptionIdempotencyService
            subscriptionIdempotencyService;
    private final SubscriptionCompensationTransactionService
            subscriptionCompensationTransactionService;
    private final SubscriptionRequestHasher subscriptionRequestHasher;

    private final WalletServiceClient walletServiceClient;
    private final HoldingServiceClient holdingServiceClient;

    public SubscriptionCompensationResult compensate(
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
                subscriptionRequestHasher.hashCompensation(
                        subscriptionId
                );

        IdempotencyOperation operation =
                IdempotencyOperation.COMPENSATE_SUBSCRIPTION;

        UUID idempotencyRequestId = UUID.randomUUID();

        int inserted = subscriptionIdempotencyService.tryBegin(
                idempotencyRequestId,
                adminId,
                operation.name(),
                idempotencyKey,
                requestHash,
                SUBSCRIPTION_RESOURCE_TYPE
        );

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

            /*
             * 이미 보상이 끝난 청약은 외부 상태를 다시 조회하거나
             * 이벤트를 발행하지 않고 기존 결과를 반환한다.
             */
            if (subscription.getSubscriptionStatus()
                    == SubscriptionStatus.REJECTED
                    || subscription.getSubscriptionStatus()
                    == SubscriptionStatus.CANCELLED) {

                SubscriptionCompensationResponse response =
                        subscriptionCompensationTransactionService
                                .completeExistingResult(
                                        subscriptionId,
                                        adminId,
                                        idempotencyKey
                                );

                return SubscriptionCompensationResult.replayed(
                        response
                );
            }

            if (subscription.getSubscriptionStatus()
                    != SubscriptionStatus.MANUAL_REVIEW) {
                throw new BusinessException(
                        SubscriptionErrorCode
                                .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                );
            }

            /*
             * 외부 HTTP 조회는 DB 쓰기 트랜잭션 밖에서 실행한다.
             */
            WalletHoldStatusResponse walletStatus =
                    getWalletStatus(subscriptionId);

            HoldingSubscriptionStatusResponse holdingStatus =
                    getHoldingStatus(subscriptionId);

            SubscriptionCompensationResponse response =
                    subscriptionCompensationTransactionService
                            .compensate(
                                    subscriptionId,
                                    adminId,
                                    idempotencyKey,
                                    normalizedCorrelationId,
                                    walletStatus,
                                    holdingStatus
                            );

            return SubscriptionCompensationResult.created(
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
     * Wallet Hold 상태를 조회한다.
     *
     * Wallet의 404는 장애가 아니라 Hold 미존재 상태로 처리한다.
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
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );
            }

            return status;

        } catch (FeignException.NotFound e) {
            /*
             * Wallet PR 계약:
             * Hold가 존재하지 않으면 404를 반환한다.
             */
            return null;

        } catch (FeignException e) {
            throw new BusinessException(
                    SubscriptionErrorCode.WALLET_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * Holding 처리 상태를 조회한다.
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
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );
            }

            return status;

        } catch (FeignException e) {
            throw new BusinessException(
                    SubscriptionErrorCode.HOLDING_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * 동일 Idempotency-Key 요청을 처리한다.
     */
    private SubscriptionCompensationResult handleExistingRequest(
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
                    SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT
            );
        }

        IdempotencyRequestStatus status =
                existing.getIdempotencyRequestStatus();

        if (status == IdempotencyRequestStatus.COMPLETED) {
            return SubscriptionCompensationResult.replayed(
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

    private SubscriptionCompensationResponse getCompletedSubscription(
            IdempotencyRequest idempotencyRequest
    ) {
        UUID resourceId = idempotencyRequest.getResourceId();

        if (resourceId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .IDEMPOTENCY_REQUEST_STATE_INVALID
            );
        }

        Subscription subscription =
                findSubscription(resourceId);

        return SubscriptionCompensationResponse.from(
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
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));
    }

    private void validateInput(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        if (subscriptionId == null || adminId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY
            );
        }
    }

    /**
     * API 명세에서 Correlation-ID는 필수가 아니므로
     * 전달되지 않았다면 서버에서 생성한다.
     */
    private String normalizeCorrelationId(
            String correlationId
    ) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return correlationId;
    }
}