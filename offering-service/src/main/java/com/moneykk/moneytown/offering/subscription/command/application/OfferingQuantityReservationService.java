package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * offering 행의 remainingQuantity 확보/복원을 각각 별도의 짧은 트랜잭션으로 처리한다.
 *
 * SubscriptionTransactionService.createSubscription()이 확보-저장-발행-멱등완료를
 * 하나의 트랜잭션으로 묶고 있으면, reserveQuantity()의 UPDATE가 잡은 offering 행 락이
 * 그 트랜잭션이 끝날 때까지(=커밋 시점까지) 유지되어 사실상 SELECT FOR UPDATE와
 * 동일한 직렬화가 일어난다. 확보만 REQUIRES_NEW로 분리해 즉시 커밋 → 즉시 락 해제되도록 한다.
 *
 * 같은 빈 안의 메서드를 this로 직접 호출하면 @Transactional 프록시를 우회해 무시되므로,
 * 별도 빈으로 분리해서 SubscriptionTransactionService가 프록시를 통해 호출하도록 한다.
 */
@Service
@RequiredArgsConstructor
public class OfferingQuantityReservationService {

    private final OfferingRepository offeringRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reserve(
            UUID offeringId,
            Long quantity,
            UUID userId
    ) {
        return offeringRepository.reserveQuantity(
                offeringId,
                quantity,
                userId
        );
    }

    /**
     * 확보 이후 단계(청약 저장, 이벤트 발행, 멱등 완료)가 실패했을 때 확보한 수량을 되돌린다.
     *
     * 호출 시점의 원래 트랜잭션은 이미 실패로 인해 rollback-only 상태이거나
     * DataIntegrityViolationException 등으로 Postgres 트랜잭션 자체가 abort된 상태일 수 있으므로,
     * 반드시 REQUIRES_NEW로 별도 트랜잭션에서 실행해야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(
            UUID offeringId,
            Long quantity
    ) {
        int restoredRows = offeringRepository.restoreQuantity(
                offeringId,
                quantity,
                JpaAuditingConfig.SYSTEM_USER_ID
        );

        if (restoredRows != 1) {
            throw new IllegalStateException(
                    "청약 생성 실패에 따른 공모 수량 복원에 실패했습니다. "
                            + "offeringId=" + offeringId
            );
        }
    }
}
