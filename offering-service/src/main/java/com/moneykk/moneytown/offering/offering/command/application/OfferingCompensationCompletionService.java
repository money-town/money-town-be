package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferingCompensationCompletionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 미해결 청약이 없는 공모의 취소를 완료한다.
     *
     * 청약 취소 또는 모집 미달 처리와 같은 트랜잭션에서 호출한다.
     * 호출부에서 청약을 잠근 경우 공모 잠금을 먼저 획득한 상태여야 한다.
     *
     * @return 이번 호출에서 공모 취소를 완료했으면 true,
     *         이미 완료됐거나 완료 조건을 충족하지 못하면 false
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeIfReady(UUID offeringId) {
        Objects.requireNonNull(
                offeringId,
                "offeringId는 필수입니다."
        );

        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        // 이미 취소된 공모의 취소 시각을 다시 변경하지 않는다.
        if (offering.getOfferingStatus() == OfferingStatus.CANCELLED) {
            return false;
        }

        if (offering.getOfferingStatus() != OfferingStatus.CANCELLING) {
            return false;
        }

        // 보상 진행 행이 없는 청약도 포함하여 미해결 여부를 확인한다.
        if (subscriptionRepository.existsUnresolvedByOfferingId(offeringId)) {
            return false;
        }

        // 취소 사유와 전체 수량 복원 여부는 엔티티에서 최종 검증한다.
        offering.completeCancellation(Instant.now());

        log.info(
                "공모 보상 완료. offeringId={}, cancellationType={}",
                offeringId,
                offering.getCancellationType()
        );

        return true;
    }
}