package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.consumer.dto.DividendPayoutDispatchPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.config.WalletRedisCacheConfig;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.producer.WalletDividendResultReadyEvent;
import com.moneykk.moneytown.wallet.producer.dto.WalletDividendResultPayload;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletDividendDispatchService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MeterRegistry meterRegistry;

    // 락을 먼저 잡고 멱등키를 나중에 확인해서 advisory lock 없이도 동시 중복에 안전함 (동시성 테스트로 검증)
    @CacheEvict(cacheNames = WalletRedisCacheConfig.WALLET_CACHE, key = "#event.payload().investorId()")
    @Transactional
    public void processDividendDispatch(EventEnvelope<DividendPayoutDispatchPayload> event) {
        DividendPayoutDispatchPayload payload = event.payload();
        String idempotencyKey = payload.payoutId().toString();

        Optional<Wallet> walletOpt = walletRepository.findByUserIdForUpdate(payload.investorId());
        if (walletOpt.isEmpty()) {
            publishFailed(event, null, WalletErrorCode.WALLET_NOT_FOUND.name());
            return;
        }
        Wallet wallet = walletOpt.get();

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            publishSucceeded(event, wallet.getId(), existing.get());
            return;
        }

        long balanceBefore = wallet.getBalance();
        try {
            wallet.deposit(payload.amount());
        } catch (BusinessException e) {
            publishFailed(event, wallet.getId(), errorCodeName(e));
            return;
        }

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.DIVIDEND, payload.amount(),
                balanceBefore, wallet.getBalance(), idempotencyKey, payload.settlementBatchId().toString()));
        recordTransactionMetric(WalletTransactionType.DIVIDEND);

        publishSucceeded(event, wallet.getId(), transaction);
        log.info("배당 지급 처리 완료: payoutId={}, walletId={}, transactionId={}, amount={}",
                payload.payoutId(), wallet.getId(), transaction.getId(), payload.amount());
        // UNIQUE 위반 등은 잡지 않고 던져 Kafka 재시도+DLT에 맡김 (PR #58 — 같은 트랜잭션에서 재조회하면 또 실패함)
    }

    // ErrorCode 인터페이스엔 name()이 없어 WalletErrorCode일 때만 꺼냄 (WalletHoldService와 동일)
    private String errorCodeName(BusinessException e) {
        if (e.getErrorCode() instanceof WalletErrorCode walletErrorCode) {
            return walletErrorCode.name();
        }
        throw e;
    }

    private void recordTransactionMetric(WalletTransactionType type) {
        meterRegistry.counter("wallet.transaction.count", "type", type.name()).increment();
    }

    private void publishSucceeded(EventEnvelope<DividendPayoutDispatchPayload> event, Long walletId,
                                   WalletTransaction transaction) {
        DividendPayoutDispatchPayload payload = event.payload();
        applicationEventPublisher.publishEvent(new WalletDividendResultReadyEvent(WalletDividendResultPayload.succeeded(
                payload.payoutId(), payload.settlementBatchId(), payload.investorId(), event.correlationId(),
                walletId, transaction.getId())));
    }

    private void publishFailed(EventEnvelope<DividendPayoutDispatchPayload> event, Long walletId, String reason) {
        DividendPayoutDispatchPayload payload = event.payload();
        applicationEventPublisher.publishEvent(new WalletDividendResultReadyEvent(WalletDividendResultPayload.failed(
                payload.payoutId(), payload.settlementBatchId(), payload.investorId(), event.correlationId(),
                walletId, reason)));
    }
}
