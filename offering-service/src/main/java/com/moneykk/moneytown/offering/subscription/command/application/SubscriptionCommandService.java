package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
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
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckRequest;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// TODO: ERROR/EXCEPTION - CODE 처리
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionCommandService {

    private static final String SUBSCRIPTION_RESOURCE_TYPE = "SUBSCRIPTION";

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final SubscriptionRequestHasher subscriptionRequestHasher;

    private final AnalysisServiceClient analysisServiceClient;

    @Transactional
    public SubscriptionCreateResponse create(
            UUID offeringId,
            UUID userId,
            String idempotencyKey,
            SubscriptionCreateRequest request
    ) {
        // TODO: User Service OpenFeign 연동 후
        // INVESTOR / accountStatus ACTIVE / kycStatus VERIFIED 검증

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
        int inserted = idempotencyRequestRepository.tryInsert(
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
         * 여기부터는 최초로 Idempotency-Key를 선점한 요청만 실행한다.
         */

        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        /*
         * 신규 멱등 요청에 대해서만 Pre-FDS 검사를 수행한다.
         *
         * requestId는 개별 Pre-FDS HTTP 요청을 식별하기 위한 UUID이며,
         * Idempotency-Key와는 별도로 생성한다.
         */
        UUID fdsRequestId = UUID.randomUUID();

        validatePreFds(
                fdsRequestId,
                userId,
                offering.getAssetId()
        );


        validateSubscriptionQuantity(
                offering,
                request.quantity()
        );

        validateDuplicateSubscription(
                offeringId,
                userId
        );

        /*
         * 조건부 UPDATE를 통해 잔여 수량을 원자적으로 확보한다.
         *
         * 성공: 1
         * 실패: 0
         */
        int updatedRows = offeringRepository.reserveQuantity(
                offeringId,
                request.quantity(),
                userId
        );

        if (updatedRows == 0) {
            // TODO: SubscriptionErrorCode 적용 후
            // S004(수량 부족) / S005(청약 불가 상태) 구분
            throw new IllegalStateException(
                    "현재 청약 가능한 공모가 아니거나 청약 가능한 수량이 부족합니다."
            );
        }

        /*
         * TODO: reservationExpiresAt 정책 확정 후
         * application.yml 또는 정책 클래스로 분리
         */
        Instant reservationExpiresAt =
                Instant.now().plus(10, ChronoUnit.MINUTES);

        /*
         * pricePerUnit은 클라이언트 입력값이 아니라
         * Offering에 저장된 가격 Snapshot을 사용한다.
         */
        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                request.quantity(),
                offering.getPricePerUnit(),
                reservationExpiresAt
        );

        Subscription savedSubscription =
                subscriptionRepository.save(subscription);

        /*
         * 청약 생성이 정상적으로 완료되었으므로
         * Idempotency 요청을 COMPLETED 상태로 전환한다.
         *
         * resourceId에는 생성된 subscriptionId를 저장하여
         * 동일 요청 재호출 시 기존 청약 결과를 반환할 수 있도록 한다.
         */
        int completed = idempotencyRequestRepository.complete(
                userId,
                operation.name(),
                idempotencyKey,
                savedSubscription.getSubscriptionId(),
                HttpStatus.ACCEPTED.value()
        );

        if (completed != 1) {
            throw new IllegalStateException(
                    "멱등 요청 완료 처리에 실패했습니다."
            );
        }

        /*
         * TODO: Outbox Pattern 적용
         *
         * Subscription 저장
         * + Idempotency COMPLETED
         * + SubscriptionReserved Outbox 저장
         *
         * 을 동일 Local Transaction에서 처리한다.
         */

        return SubscriptionCreateResponse.from(savedSubscription);
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
                                new IllegalStateException(
                                        "멱등 요청 정보를 찾을 수 없습니다."
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
            // TODO: SubscriptionErrorCode S006 적용
            throw new IllegalStateException(
                    "동일 멱등 키에 다른 요청 데이터가 전달되었습니다."
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
            throw new IllegalStateException(
                    "동일한 청약 요청이 현재 처리 중입니다."
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
            throw new IllegalStateException(
                    "이전에 실패한 멱등 요청입니다."
            );
        }

        throw new IllegalStateException(
                "알 수 없는 멱등 요청 상태입니다."
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
            throw new IllegalStateException(
                    "완료된 멱등 요청의 청약 ID가 존재하지 않습니다."
            );
        }

        Subscription subscription = subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(resourceId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "기존 청약 결과를 찾을 수 없습니다."
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
            throw new IllegalArgumentException(
                    "Idempotency-Key는 필수입니다."
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key는 100자를 초과할 수 없습니다."
            );
        }
    }

    /**
     * 신규 청약 요청에 대해 Pre-FDS 검사를 수행한다.
     *
     * FDS가 PASS를 반환한 경우에만 이후 청약 로직을 진행한다.
     * BLOCK 또는 정상적인 결과를 받을 수 없는 경우 청약 처리를 중단한다.
     */
    private void validatePreFds(
            UUID requestId,
            UUID userId,
            UUID assetId
    ) {
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
            // TODO: SubscriptionErrorCode 적용 후 S018
            throw new IllegalStateException(
                    "현재 청약 검증 서비스를 사용할 수 없습니다."
            );
        }

        if (result.isBlock()) {
            // TODO: SubscriptionErrorCode 적용 후 S017
            throw new IllegalStateException(
                    "이상 거래 탐지 정책에 의해 청약이 제한되었습니다."
            );
        }

        if (!result.isPass()) {
            // 알 수 없는 응답은 Fail Closed 처리
            // TODO: SubscriptionErrorCode 적용 후 S018
            throw new IllegalStateException(
                    "현재 청약 검증 서비스를 사용할 수 없습니다."
            );
        }
    }

    /**
     * 동일 사용자가 동일 공모에 이미 청약했는지 확인한다.
     */
    private void validateDuplicateSubscription(
            UUID offeringId,
            UUID userId
    ) {
        boolean exists =
                subscriptionRepository
                        .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                                offeringId,
                                userId
                        );

        if (exists) {
            // TODO: SubscriptionErrorCode 적용 후 S003으로 변경
            throw new IllegalStateException(
                    "이미 청약한 공모입니다."
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

            // TODO: SubscriptionErrorCode 적용 후 S001로 변경
            throw new IllegalArgumentException(
                    "청약 수량이 유효하지 않습니다."
            );
        }
    }
}