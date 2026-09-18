package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionConfirmationBatchTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionBatchConfirmationService subscriptionBatchConfirmationService;

    /**
     * 최종 확정 가능한 공모 한 건을 선점하고
     * HOLD_SUCCEEDED 청약 한 배치를 확정한다.
     *
     * 공모 선점부터 청약 잠금, 상태 변경, Outbox 저장까지
     * 하나의 독립 트랜잭션에서 처리한다.
     *
     * 여러 인스턴스에서 동시에 실행되면 OfferingRepository의
     * FOR UPDATE SKIP LOCKED에 의해 서로 다른 공모를 처리한다.
     *
     * 처리 도중 예외가 발생하면 해당 배치의 청약 상태 변경과
     * Outbox 저장이 함께 롤백된다. 남은 HOLD_SUCCEEDED 청약은
     * 다음 스케줄 실행에서 다시 조회된다.
     *
     * @return 이번 트랜잭션에서 확정한 청약 수.
     *         처리 대상 공모가 없으면 0
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int confirmNextBatch() {
        Offering offering =
                offeringRepository
                        .findNextConfirmationTargetForUpdate()
                        .orElse(null);

        if (offering == null) {
            return 0;
        }

        String correlationId =
                UUID.randomUUID().toString();

        return subscriptionBatchConfirmationService
                .confirmNextBatchIfReady(
                        offering,
                        correlationId
                );
    }
}