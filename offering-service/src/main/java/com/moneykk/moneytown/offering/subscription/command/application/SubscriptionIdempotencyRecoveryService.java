package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SubscriptionIdempotencyRecoveryService {

    private final IdempotencyRequestRepository idempotencyRequestRepository;

    /**
     * PROCESSING 상태를 유지할 수 있는 최대 시간.
     *
     * 기본값은 5분이며, 이 시간을 초과한 요청은
     * 서버 종료 등으로 정상 완료되지 못한 요청으로 판단한다.
     */
    @Value("${subscription.idempotency.processing-timeout-seconds:300}")
    private long processingTimeoutSeconds;

    /**
     * 오래된 PROCESSING 멱등 요청을 FAILED 상태로 복구한다.
     *
     * 복구된 Idempotency-Key는 다시 사용하지 않고
     * 새로운 Idempotency-Key로 요청해야 한다.
     *
     * @param batchSize 한 번에 복구할 최대 요청 수
     * @return FAILED 상태로 변경된 요청 수
     */
    @Transactional
    public int recoverExpiredProcessing(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize는 1 이상이어야 합니다."
            );
        }

        if (processingTimeoutSeconds <= 0) {
            throw new IllegalStateException(
                    "멱등 요청 처리 제한 시간은 1초 이상이어야 합니다."
            );
        }

        Instant now =
                idempotencyRequestRepository.getCurrentDatabaseTime();

        Instant expiredBefore =
                now.minusSeconds(processingTimeoutSeconds);

        return idempotencyRequestRepository
                .recoverExpiredProcessingRequests(
                        expiredBefore,
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        batchSize
                );
    }
}