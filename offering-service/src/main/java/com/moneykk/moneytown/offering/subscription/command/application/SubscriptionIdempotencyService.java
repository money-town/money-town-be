package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionIdempotencyService {

    private final IdempotencyRequestRepository idempotencyRequestRepository;

    /**
     * 청약 멱등 요청을 별도 트랜잭션에서 선점한다.
     *
     * 이후 User/FDS 호출 또는 청약 처리에서 예외가 발생해도
     * PROCESSING 선점 기록 자체는 롤백되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int tryBegin(
            UUID idempotencyRequestId,
            UUID userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            String resourceType
    ) {
        return idempotencyRequestRepository.tryInsert(
                idempotencyRequestId,
                userId,
                operation,
                idempotencyKey,
                requestHash,
                resourceType
        );
    }

    /**
     * 청약 처리 실패를 별도 트랜잭션으로 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            UUID userId,
            String operation,
            String idempotencyKey,
            int responseCode
    ) {
        idempotencyRequestRepository.fail(
                userId,
                operation,
                idempotencyKey,
                responseCode
        );
    }
}