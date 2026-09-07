package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.producer.WalletEventPublisher;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletHoldService {

    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletEventPublisher walletEventPublisher;

    @Transactional
    public void processReservation(EventEnvelope<SubscriptionReservedPayload> event) // HOLD
    {
        String aggregateId = event.aggregateId();
        UUID subscriptionId = UUID.fromString(aggregateId);

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

        WalletHold hold = walletHoldRepository.save(new WalletHold(wallet.getId(), subscriptionId, amount));

        walletEventPublisher.publishHoldResult(
                WalletHoldResultPayload.succeeded(aggregateId, event.userId(), event.correlationId(), hold.getId(), wallet.getId()));
    }

    @Transactional
    public void confirmHold(EventEnvelope<Object> event)  // DEDUCT
    {
        UUID subscriptionId = UUID.fromString(event.aggregateId());

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
    }

    @Transactional
    public void compensateHold(EventEnvelope<SubscriptionCompensationRequestedPayload> event) // UNHOLD/REFUND
    {
        String aggregateId = event.aggregateId();
        UUID subscriptionId = UUID.fromString(aggregateId);

        Optional<WalletHold> holdOpt = walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId);
        if (holdOpt.isEmpty()) {
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

    // BusinessException.getErrorCode()는 ErrorCode 인터페이스라 name()이 없음 — wallet.hold()가
    // 던지는 예외는 전부 WalletErrorCode 인스턴스이므로 캐스팅해서 enum 이름을 그대로 꺼낸다.
    private String errorCodeName(BusinessException e) {
        return ((WalletErrorCode) e.getErrorCode()).name();
    }

    private void releaseHold(String subscriptionId, UUID userId, String correlationId, Wallet wallet, WalletHold hold) {
        long balanceBefore = wallet.getBalance();
        wallet.releaseHold(hold.getAmount());
        hold.release();

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.UNHOLD, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "UNHOLD:" + subscriptionId, subscriptionId
        ));

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

        walletEventPublisher.publishCompensationResult(WalletCompensationResultPayload.succeeded(
                subscriptionId, userId, correlationId, hold.getId(), wallet.getId(), "REFUND", transaction.getId(), hold.getAmount()));
    }
}
