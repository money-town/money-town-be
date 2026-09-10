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
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResult;
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
    private final SubscriptionLimitExceededEventService subscriptionLimitExceededEventService;

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final SubscriptionRequestHasher subscriptionRequestHasher;

    // openFeignClient
    private final AnalysisServiceClient analysisServiceClient;
    private final UserServiceClient userServiceClient;

    public SubscriptionCreateResult create(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            SubscriptionCreateRequest request,
            String correlationId
    ) {

        validateIdempotencyKey(idempotencyKey);
        validateCorrelationId(correlationId);

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

        UUID idempotencyRequestId = UUID.randomUUID();

        int inserted = subscriptionIdempotencyService.tryBegin(
                idempotencyRequestId,
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
             * userRole == INVESTOR
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
            Offering offering = findOffering(offeringId);

            /*
             * OPEN 상태이며 실제 모집 기간 내에 있는지 먼저 확인한다.
             *
             * reserveQuantity()의 조건부 UPDATE와 별개로 수행한다.
             * 여기서는 사용자에게 실패 원인을 구분해서 제공하기 위한
             * 사전 비즈니스 검증을 담당한다.
             */
            validateOfferingAvailable(offering);

            /*
             * 요청 수량이 존재하고 최소 청약 수량 이상인지 검증한다.
             *
             * 최대 청약 수량 초과는 PostFDS 이벤트를 발행해야 하므로
             * 아래 분기에서 별도로 처리한다.
             */
            validateSubscriptionQuantity(
                    offering,
                    request.quantity()
            );

            if (request.quantity()
                    > offering.getMaxSubscriptionQuantity()) {

                subscriptionLimitExceededEventService.recordLimitExceeded(
                        idempotencyRequestId,
                        userId,
                        idempotencyKey,
                        offering.getAssetId(),
                        request.quantity(),
                        offering.getMaxSubscriptionQuantity(),
                        correlationId
                );

                /*
                 * recordLimitExceeded()의 REQUIRES_NEW 트랜잭션이 완료된 후
                 * API 요청을 한도 초과 오류로 종료한다.
                 */
                throw new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                );
            }

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
             * - SubscriptionReserved Outbox 저장
             */

            SubscriptionCreateResponse response =
                    subscriptionTransactionService.createSubscription(
                            offering.getOfferingId(),
                            userId,
                            idempotencyKey,
                            request.quantity(),
                            offering.getPricePerUnit(),
                            correlationId
                    );

            return SubscriptionCreateResult.created(response);

        } catch (BusinessException e) {

            /*
             * 한도 초과는 멱등 실패 기록과 Outbox 저장을
             * SubscriptionLimitExceededEventService에서 이미 완료했다.
             */
            if (e.getErrorCode()
                    != SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED) {

                subscriptionIdempotencyService.fail(
                        userId,
                        operation.name(),
                        idempotencyKey,
                        e.getErrorCode().getStatus().value()
                );
            }

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
    private SubscriptionCreateResult handleExistingRequest(
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
            return SubscriptionCreateResult.replayed(
                    getCompletedSubscription(existing)
            );
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
             * FAILED 상태의 요청은 동일한 Idempotency-Key로 재시도하지 않는다.
             * 클라이언트는 새로운 요청에 새로운 Idempotency-Key를 사용해야 한다.
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
     * 멱등 요청을 선점하기 전에 Correlation-ID를 검증한다.
     */
    private void validateCorrelationId(
            String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
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

            PreFdsCheckResponse result = response != null ? response.data() : null;

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
     * userRole이 INVESTOR이고,
     * accountStatus가 ACTIVE이며,
     * kycStatus가 VERIFIED이고,
     * 현재 시각이 kycExpiresAt 이전인 경우에만 청약을 진행한다.
     *
     * User Service를 정상적으로 조회할 수 없는 경우에는
     * 최신 사용자 상태를 확인할 수 없으므로 Fail Closed 처리한다.
     */
    private void validateUserEligibility(UUID userId) {

        try {
            ApiResponse<UserInvestmentEligibilityResponse> response =
                    userServiceClient.getInvestmentEligibility(userId);

            UserInvestmentEligibilityResponse user =
                    response != null
                            ? response.data()
                            : null;

            validateUserEligibilityResponse(
                    userId,
                    user
            );

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
     * User Service 응답의 필수값과 요청 사용자 일치 여부를 검증한다.
     *
     * 실제 청약 자격은 UserInvestmentEligibilityResponse의
     * isEligibleForSubscription()에서 판단한다.
     */
    private void validateUserEligibilityResponse(
            UUID requestedUserId,
            UserInvestmentEligibilityResponse user
    ) {
        if (user == null
                || user.userId() == null
                || !requestedUserId.equals(user.userId())
                || user.userRole() == null
                || user.accountStatus() == null
                || user.kycStatus() == null
                || user.kycExpiresAt() == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }
    }

    /**
     * 요청 수량이 존재하고 공모의 최소 청약 수량 이상인지 검증한다.
     *
     * 최대 청약 수량 초과는 PostFDS 이벤트를 저장해야 하므로
     * create()에서 별도로 처리한다.
     */
    private void validateSubscriptionQuantity(
            Offering offering,
            Long quantity
    ) {
        if (quantity == null
                || quantity < offering.getMinSubscriptionQuantity()) {

            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
            );
        }
    }

    private Offering findOffering(UUID offeringId) {
        return offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );
    }
}