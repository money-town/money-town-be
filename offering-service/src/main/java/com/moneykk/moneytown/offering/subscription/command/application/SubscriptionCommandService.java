package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.AnalysisServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckRequest;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionCommandService {

    private static final String SUBSCRIPTION_RESOURCE_TYPE = "SUBSCRIPTION";

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    private final SubscriptionIdempotencyService subscriptionIdempotencyService;
    private final SubscriptionTransactionService subscriptionTransactionService;

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final SubscriptionRequestHasher subscriptionRequestHasher;

    // openFeignClient
    private final AnalysisServiceClient analysisServiceClient;
    private final UserServiceClient userServiceClient;

    public SubscriptionCreateResponse create(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            SubscriptionCreateRequest request
    ) {
        // TODO: SubscriptionReserved Outbox 저장 추가

        validateIdempotencyKey(idempotencyKey);

        String requestHash = subscriptionRequestHasher.hash(
                offeringId,
                request.quantity()
        );

        IdempotencyOperation operation =
                IdempotencyOperation.CREATE_SUBSCRIPTION;

        /*
         * 동일 사용자 + 동일 작업 + 동일 Idempotency-Key에 대해
         * 최초 요청만 PROCESSING 상태로 선점한다.
         *
         * DB UNIQUE(user_id, operation, idempotency_key)와
         * ON CONFLICT DO NOTHING을 이용하여
         * 동시에 동일 요청이 들어오더라도 중복 실행을 방지한다.
         */
        int inserted = subscriptionIdempotencyService.tryBegin(
                UUID.randomUUID(),
                userId,
                operation.name(),
                idempotencyKey,
                requestHash,
                SUBSCRIPTION_RESOURCE_TYPE
        );

        /*
         * 이미 동일 Idempotency-Key가 존재하는 경우
         * 기존 요청의 상태와 requestHash를 확인한다.
         */
        if (inserted == 0) {
            return handleExistingRequest(
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash
            );
        }

        /*
         * 여기부터는 최초로 Idempotency-Key를 선점한 신규 요청만 실행한다.
         *
         * User Service / FDS와의 외부 HTTP 통신은
         * DB Write Transaction 밖에서 수행한다.
         */
        try {
            /*
             * 최신 사용자 상태를 조회하여 청약 자격을 검증한다.
             *
             * accountStatus == ACTIVE
             * kycStatus == VERIFIED
             * 현재 시각 < kycExpiresAt
             */
            validateUserEligibility(userId);

            /*
             * 청약 대상 공모를 조회한다.
             *
             * 이 시점에는 DB Write Transaction을 시작하지 않는다.
             */
            Offering offering = offeringRepository
                    .findByOfferingIdAndIsDeletedFalse(offeringId)
                    .orElseThrow(() ->
                            new BusinessException(
                                    OfferingErrorCode.OFFERING_NOT_FOUND
                            )
                    );

            /*
             * OPEN 상태이며 실제 모집 기간 내에 있는지 먼저 확인한다.
             *
             * reserveQuantity()의 조건부 UPDATE와 별개로 수행한다.
             * 여기서는 사용자에게 실패 원인을 구분해서 제공하기 위한
             * 사전 비즈니스 검증을 담당한다.
             */
            validateOfferingAvailable(offering);

            /*
             * 공모의 최소·최대 청약 수량을 검증한다.
             *
             * DB 값을 변경하지 않는 검증이므로
             * Transaction Service 진입 전에 수행한다.
             */
            validateSubscriptionQuantity(
                    offering,
                    request.quantity()
            );

            /*
             * 신규 청약 요청에 대해서만 Pre-FDS 검사를 수행한다.
             *
             * requestId는 개별 Pre-FDS HTTP 요청 식별자이며
             * Idempotency-Key와 별도로 생성한다.
             */
            validatePreFds(
                    UUID.randomUUID(),
                    userId,
                    offering.getAssetId()
            );

            /*
             * 실제 DB 변경이 필요한 구간만 별도 Transaction Service에서 수행한다.
             *
             * - 중복 청약 확인
             * - remainingQuantity 조건부 UPDATE
             * - Subscription PROCESSING 생성
             * - Idempotency COMPLETED 처리
             * - 추후 SubscriptionReserved Outbox 저장
             */
            return subscriptionTransactionService.createSubscription(
                    offering.getOfferingId(),
                    userId,
                    idempotencyKey,
                    request.quantity(),
                    offering.getPricePerUnit()
            );

        } catch (BusinessException e) {

            subscriptionIdempotencyService.fail(
                    userId,
                    operation.name(),
                    idempotencyKey,
                    e.getErrorCode().getStatus().value()
            );

            throw e;

        } catch (RuntimeException e) {

            subscriptionIdempotencyService.fail(
                    userId,
                    operation.name(),
                    idempotencyKey,
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            );

            throw e;
        }

    }

    /**
     * 이미 존재하는 Idempotency-Key 요청을 처리한다.
     *
     * 동일 Key + 동일 요청:
     * - COMPLETED  → 기존 청약 결과 반환
     * - PROCESSING → 중복 실행하지 않고 처리 중 응답
     *
     * 동일 Key + 다른 요청:
     * - 멱등 키 충돌 처리
     */
    private SubscriptionCreateResponse handleExistingRequest(
            UUID userId,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRequest existing =
                idempotencyRequestRepository
                        .findByUserIdAndOperationAndIdempotencyKey(
                                userId,
                                operation,
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
                                )
                        );

        /*
         * 같은 Idempotency-Key를 사용했지만
         * 실제 요청 데이터가 다른 경우.
         *
         * 예:
         * 최초 quantity = 10
         * 재요청 quantity = 20
         */
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT
            );
        }

        IdempotencyRequestStatus status =
                existing.getIdempotencyRequestStatus();

        if (status == IdempotencyRequestStatus.COMPLETED) {
            return getCompletedSubscription(existing);
        }

        if (status == IdempotencyRequestStatus.PROCESSING) {
            /*
             * 아직 최초 요청 처리가 끝나지 않았으므로
             * 수량 차감, Pre-FDS 등을 다시 실행하지 않는다.
             */
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_REQUEST_PROCESSING
            );
        }

        if (status == IdempotencyRequestStatus.FAILED) {
            /*
             * TODO:
             * FAILED 상태의 동일 Idempotency-Key를
             * 다시 사용할 수 있도록 할 것인지 정책 확정 필요.
             *
             * 현재는 자동 재처리하지 않는다.
             */
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_REQUEST_FAILED
            );
        }

        throw new BusinessException(
                SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
        );
    }

    /**
     * 완료된 멱등 요청에 연결된 기존 청약 결과를 반환한다.
     */
    private SubscriptionCreateResponse getCompletedSubscription(
            IdempotencyRequest idempotencyRequest
    ) {
        UUID resourceId = idempotencyRequest.getResourceId();

        if (resourceId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
            );
        }

        Subscription subscription = subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(resourceId)
                .orElseThrow(() ->
                        new BusinessException(
                                SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
                        )
                );

        return SubscriptionCreateResponse.from(subscription);
    }

    /**
     * Idempotency-Key 헤더 값을 검증한다.
     */
    private void validateIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY
            );
        }
    }

    /**
     * 청약 대상 공모가 현재 실제 청약 가능한 상태인지 검증한다.
     *
     * - OPEN 상태
     * - 모집 시작 시각 도달
     * - 모집 종료 시각 이전
     *
     * 이 검증은 사용자에게 정확한 비즈니스 실패 원인을
     * 반환하기 위한 사전 검증이다.
     *
     * 실제 수량 차감 시에는 reserveQuantity()의 조건부 UPDATE가
     * 상태/기간/잔여 수량을 다시 검증한다.
     */
    private void validateOfferingAvailable(
            Offering offering
    ) {

        if (offering.getOfferingStatus() != OfferingStatus.OPEN) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_NOT_AVAILABLE
            );
        }

        Instant now = Instant.now();

        if (now.isBefore(offering.getStartAt())
                || !now.isBefore(offering.getEndAt())) {

            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_NOT_AVAILABLE
            );
        }
    }

    /**
     * 신규 청약 요청에 대해 Pre-FDS 검사를 수행한다.
     *
     * FDS가 PASS를 반환한 경우에만 이후 청약 로직을 진행한다.
     * BLOCK 또는 FDS 검사를 정상적으로 수행할 수 없는 경우
     * Fail Closed 정책에 따라 청약 처리를 중단한다.
     */
    private void validatePreFds(
            UUID requestId,
            UUID userId,
            UUID assetId
    ) {
        try {
            ApiResponse<PreFdsCheckResponse> response =
                    analysisServiceClient.check(
                            new PreFdsCheckRequest(
                                    requestId,
                                    userId,
                                    assetId
                            )
                    );

            PreFdsCheckResponse result = response.data();

            if (result == null) {
                throw new BusinessException(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );
            }

            /*
             * BLOCK은 FDS 장애가 아니라
             * 정상적으로 수행된 업무 판단 결과이다.
             */
            if (result.isBlock()) {
                throw new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_BLOCKED_BY_FDS
                );
            }

            /*
             * PASS / BLOCK 이외의 알 수 없는 응답은
             * 정상적인 FDS 판단으로 간주하지 않고 Fail Closed 처리한다.
             */
            if (!result.isPass()) {
                throw new BusinessException(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );
            }

        } catch (FeignException e) {
            /*
             * FDS 호출 과정에서 4xx/5xx 또는 통신 오류가 발생하면
             * 정상적인 FDS 판단 결과를 확인할 수 없으므로
             * Fail Closed 정책에 따라 청약을 중단한다.
             */
            throw new BusinessException(
                    SubscriptionErrorCode.FDS_SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * User Service에서 최신 사용자 상태를 조회하고
     * 청약 가능 여부를 검증한다.
     *
     * accountStatus가 ACTIVE이고,
     * kycStatus가 VERIFIED이며,
     * 현재 시각이 kycExpiresAt 이전인 경우에만 청약을 진행한다.
     *
     * User Service를 정상적으로 조회할 수 없는 경우에는
     * 최신 사용자 상태를 확인할 수 없으므로 Fail Closed 처리한다.
     */
    private void validateUserEligibility(UUID userId) {

        try {
            ApiResponse<UserInvestmentEligibilityResponse> response =
                    userServiceClient.getInvestmentEligibility(userId);

            UserInvestmentEligibilityResponse user = response.data();

            if (user == null) {
                throw new BusinessException(
                        SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
                );
            }

            Instant now = Instant.now();

            if (!user.isEligibleForSubscription(now)) {
                throw new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_ELIGIBILITY_NOT_MET
                );
            }

        } catch (FeignException.NotFound e) {
            /*
             * User Service에서 사용자를 찾을 수 없는 경우.
             *
             * 실제 User Service 정책 확정 후
             * 미존재 사용자와 논리 삭제 사용자의 404 처리 계약을 확인한다.
             */
            throw new BusinessException(
                    SubscriptionErrorCode.USER_NOT_FOUND
            );

        } catch (FeignException e) {
            /*
             * User Service 4xx/5xx, Timeout, Connection Failure 등
             * 정상적인 사용자 상태를 확인할 수 없는 경우.
             *
             * 최신 사용자 상태를 확인할 수 없으므로
             * Fail Closed 정책에 따라 청약을 진행하지 않는다.
             */
            throw new BusinessException(
                    SubscriptionErrorCode.USER_SERVICE_UNAVAILABLE
            );
        }
    }


    /**
     * 공모별 최소·최대 청약 수량 범위를 검증한다.
     */
    private void validateSubscriptionQuantity(
            Offering offering,
            Long quantity
    ) {
        if (quantity == null
                || quantity < offering.getMinSubscriptionQuantity()
                || quantity > offering.getMaxSubscriptionQuantity()) {

            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
            );
        }
    }
}