package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionTimeoutService {

    private static final int TIMEOUT_BATCH_SIZE = 100;

    private final SubscriptionRepository subscriptionRepository;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 조회하여
     * 후속 보상을 위한 COMPENSATING 상태로 전환한다.
     *
     * TODO: Wallet 타임아웃 보상 계약 확정 후 후속 처리 연결
     * - HOLD 미존재 시 NONE 성공 처리
     * - 늦은 SubscriptionReserved에 의한 HOLD 생성 차단
     * - RELEASE / REFUND 결과 수신
     * - 공모 수량 복원
     * - COMPENSATING -> REJECTED 완료
     *
     * TODO: 대량 타임아웃 발생 시
     * 배치 크기 및 반복 처리 방식의 성능을 검증한다.
     *
     * @return timeout 처리된 청약 수
     */
    @Transactional
    public int processExpiredReservations() {

        Instant now = Instant.now();

        List<Subscription> subscriptions =
                subscriptionRepository
                        .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                                SubscriptionStatus.PROCESSING,
                                now,
                                PageRequest.of(0, TIMEOUT_BATCH_SIZE)
                        );

        for (Subscription subscription : subscriptions) {
            subscription.startExpirationCompensation(now);
        }

        return subscriptions.size();
    }
}
