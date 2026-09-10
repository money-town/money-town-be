package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletExpiredReservation;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.producer.WalletEventPublisher;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import com.moneykk.moneytown.wallet.repository.WalletExpiredReservationRepository;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletHoldService {

    private static final String REASON_RESERVATION_EXPIRED = "RESERVATION_EXPIRED";

    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletExpiredReservationRepository walletExpiredReservationRepository;
    private final WalletEventPublisher walletEventPublisher;
    private final EntityManager entityManager;
    private final MeterRegistry meterRegistry;

    // 거래 타입별 처리량을 Grafana에서 볼 수 있도록 카운터로 남긴다.
    private void recordTransactionMetric(WalletTransactionType type) {
        meterRegistry.counter("wallet.transaction.count", "type", type.name()).increment();
    }

    @Transactional
    public void processReservation(EventEnvelope<SubscriptionReservedPayload> event) // HOLD
    {
        String aggregateId = event.aggregateId();
        UUID subscriptionId = UUID.fromString(aggregateId);

        // compensateHold()의 만료 기록과 동시에 들어와도 둘 중 하나만 반영되도록 직렬화
        acquireSubscriptionLock(subscriptionId);

        // 타임아웃으로 이미 만료 처리된 청약이면, 뒤늦게 도착한 예약이라 HOLD를 만들지 않고 조용히 종료
        if (walletExpiredReservationRepository.existsById(subscriptionId)) {
            log.info("만료 처리된 청약에 뒤늦게 도착한 SubscriptionReserved 무시: subscriptionId={}", subscriptionId);
            return;
        }

        // p_wallet_holds.subscription_id UNIQUE 제약 덕분에 자연 멱등 — 이미 처리된 청약이면 조용히 종료
        if (walletHoldRepository.findBySubscriptionId(subscriptionId).isPresent()) {
            return;
        }

        Optional<Wallet> walletOpt = walletRepository.findByUserIdForUpdate(event.userId());
        if (walletOpt.isEmpty()) {
            walletEventPublisher.publishHoldResult(WalletHoldResultPayload.failed(
                    aggregateId, event.userId(), event.correlationId(), null, WalletErrorCode.WALLET_NOT_FOUND.name()));
            return;
        }

        Wallet wallet = walletOpt.get();
        long amount = event.payload().amount();

        long balanceBefore = wallet.getBalance();
        try {
            wallet.hold(amount);
        } catch (BusinessException e) {
            // PostFDS 연동을 위해 Offering이 이 reason을 그대로 저장·전달하기로 확정 — 실제 실패 원인 그대로 전달
            walletEventPublisher.publishHoldResult(WalletHoldResultPayload.failed(
                    aggregateId, event.userId(), event.correlationId(), wallet.getId(), errorCodeName(e)));
            return;
        } // 예외 X, 결과를 이벤트로만 알림

        walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.HOLD, amount, balanceBefore, wallet.getBalance(),
                "HOLD:" + subscriptionId, aggregateId
        ));
        recordTransactionMetric(WalletTransactionType.HOLD);

        WalletHold hold = walletHoldRepository.save(new WalletHold(wallet.getId(), subscriptionId, amount));

        walletEventPublisher.publishHoldResult(
                WalletHoldResultPayload.succeeded(aggregateId, event.userId(), event.correlationId(), hold.getId(), wallet.getId()));
    }

    @Transactional
    public void confirmHold(EventEnvelope<Object> event)  // DEDUCT
    {
        UUID subscriptionId = UUID.fromString(event.aggregateId());

        // HOLD 커밋 전에 확정 이벤트가 먼저 도착하는 레이스 방지
        acquireSubscriptionLock(subscriptionId);

        WalletHold hold = walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId).orElse(null);
        // hold가 없거나 이미 COMMITTED면 중복 수신 — 조용히 종료 (재차감 방지)
        if (hold == null || hold.getStatus() != WalletHoldStatus.HELD) {
            return;
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(event.userId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        long balanceBefore = wallet.getBalance();
        wallet.deductHold(hold.getAmount());
        hold.commit();

        walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.DEDUCT, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "DEDUCT:" + subscriptionId, subscriptionId.toString()
        ));
        recordTransactionMetric(WalletTransactionType.DEDUCT);
    }

    @Transactional
    public void compensateHold(EventEnvelope<SubscriptionCompensationRequestedPayload> event) // UNHOLD/REFUND
    {
        String aggregateId = event.aggregateId();
        UUID subscriptionId = UUID.fromString(aggregateId);

        // processReservation()의 HOLD 생성과 동시에 들어와도 둘 중 하나만 반영되도록 직렬화
        acquireSubscriptionLock(subscriptionId);

        Optional<WalletHold> holdOpt = walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId);
        if (holdOpt.isEmpty()) {
            if (REASON_RESERVATION_EXPIRED.equals(event.payload().reason())) {
                recordExpiredReservation(subscriptionId, event.payload().reason());
                walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.succeeded(
                        aggregateId, event.userId(), event.correlationId(), null, null, "NONE", null, null));
                return;
            }

            walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.failed(
                    aggregateId, event.userId(), event.correlationId(), null, null, "HOLD_NOT_FOUND"));
            return;
        }

        WalletHold hold = holdOpt.get();
        Wallet wallet = walletRepository.findByUserIdForUpdate(event.userId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        switch (hold.getStatus()) {
            case HELD -> releaseHold(aggregateId, event.userId(), event.correlationId(), wallet, hold); // UNHOLD
            case COMMITTED -> refundHold(aggregateId, event.userId(), event.correlationId(), wallet, hold); // REFUND
            case RELEASED, REFUNDED -> walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.succeeded(
                    aggregateId, event.userId(), event.correlationId(), hold.getId(), wallet.getId(), "NONE", null, null)
            ); // 이미 처리됨 (재전송된 보상 요청)
        }
    }

    // BusinessException.getErrorCode()는 ErrorCode 인터페이스라 name()이 없음 — WalletErrorCode일 때만
    // enum 이름을 꺼내 실패 이벤트로 변환하고, 그 외(예상 못한) ErrorCode는 원래 예외 그대로 다시 던져서
    // 실패 이벤트로 뭉개지 않고 Kafka 재시도 대상이 되게 한다.
    private String errorCodeName(BusinessException e) {
        if (e.getErrorCode() instanceof WalletErrorCode walletErrorCode) {
            return walletErrorCode.name();
        }
        throw e;
    }

    private void releaseHold(String subscriptionId, UUID userId, String correlationId, Wallet wallet, WalletHold hold) {
        long balanceBefore = wallet.getBalance();
        wallet.releaseHold(hold.getAmount());
        hold.release();

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.UNHOLD, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "UNHOLD:" + subscriptionId, subscriptionId
        ));
        recordTransactionMetric(WalletTransactionType.UNHOLD);

        walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.succeeded(
                subscriptionId, userId, correlationId, hold.getId(), wallet.getId(), "RELEASE", transaction.getId(), hold.getAmount()));
    }

    private void refundHold(String subscriptionId, UUID userId, String correlationId, Wallet wallet, WalletHold hold) {
        long balanceBefore = wallet.getBalance();
        wallet.deposit(hold.getAmount());
        hold.refund();

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.REFUND, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "REFUND:" + subscriptionId, subscriptionId
        ));
        recordTransactionMetric(WalletTransactionType.REFUND);

        walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.succeeded(
                subscriptionId, userId, correlationId, hold.getId(), wallet.getId(), "REFUND", transaction.getId(), hold.getAmount()));
    }

    // subscriptionId 기준으로 processReservation()/confirmHold()/compensateHold()를 직렬화한다.
    // 트랜잭션 스코프 락이라 커밋/롤백 시 자동 해제됨.
    private void acquireSubscriptionLock(UUID subscriptionId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", subscriptionId.getMostSignificantBits())
                .getSingleResult();
    }

    private void recordExpiredReservation(UUID subscriptionId, String reason) {
        // 같은 보상 요청이 재발행돼도(Outbox 미구현으로 인한 재전송 등) 중복 기록하지 않음
        if (walletExpiredReservationRepository.existsById(subscriptionId)) {
            return;
        }
        walletExpiredReservationRepository.save(new WalletExpiredReservation(subscriptionId, reason));
    }
}
