package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionRequestRecoveryService {

    private final SubscriptionRequestRepository requestRepository;
    private final SubscriptionEventPublisher eventPublisher;
    private final SubscriptionRequestMetrics metrics;

    @Value("${subscription.request.recovery.stuck-seconds:300}")
    private long stuckSeconds;

    @Value("${subscription.request.recovery.batch-size:100}")
    private int batchSize;

    /**
     * Consumer 종료 등으로 PROCESSING에 남은 접수를 다시 QUEUED로 바꾸고,
     * 같은 트랜잭션에서 재처리 Outbox 이벤트를 저장한다.
     */
    @Transactional
    public int recoverStuckRequests() {
        if (stuckSeconds <= 0 || batchSize <= 0) {
            throw new IllegalStateException("청약 접수 복구 설정은 1 이상이어야 합니다.");
        }

        Instant stuckBefore = Instant.now().minusSeconds(stuckSeconds);
        List<SubscriptionRequest> requests =
                requestRepository.findStuckProcessingForUpdate(
                        stuckBefore,
                        batchSize
                );

        for (SubscriptionRequest request : requests) {
            request.requeue();
            eventPublisher.publishRequested(request);
        }

        metrics.recordRecovered(requests.size());
        return requests.size();
    }
}
