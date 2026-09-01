package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionCommandService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public SubscriptionCreateResponse create(
            UUID offeringId,
            UUID userId,
            SubscriptionCreateRequest request
    ) {
        // TODO: Idempotency-Key 검증 추가
        // TODO: User Service OpenFeign 연동 후
        // INVESTOR / accountStatus ACTIVE / kycStatus VERIFIED 검증
        // TODO: Analysis Service Pre-FDS 연동 후 신규 요청만 PASS/BLOCK 검증
        // TODO: SubscriptionReserved Outbox 저장 추가
        // TODO: ERROR/EXCEPTION - CODE 처리

        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        validateSubscriptionQuantity(
                offering,
                request.quantity()
        );

        validateDuplicateSubscription(
                offeringId,
                userId
        );

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

        // TODO: reservationExpiresAt 정책 확정 후 설정값 분리
        Instant reservationExpiresAt =
                Instant.now().plus(10, ChronoUnit.MINUTES);

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                request.quantity(),
                offering.getPricePerUnit(),
                reservationExpiresAt
        );

        Subscription savedSubscription =
                subscriptionRepository.save(subscription);

        return SubscriptionCreateResponse.from(savedSubscription);
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