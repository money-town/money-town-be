package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedEvent;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionConfirmedEvent;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedEvent;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.producer.WalletEventPublisher;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultEvent;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultEvent;
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
    public void processReservation(SubscriptionReservedEvent event) // HOLD
    {
        UUID subscriptionId = event.aggregateId();

        // p_wallet_holds.subscription_id UNIQUE 제약 덕분에 자연 멱등 — 이미 처리된 청약이면 조용히 종료
        if (walletHoldRepository.findBySubscriptionId(subscriptionId).isPresent()) {
            return;
        }

        Optional<Wallet> walletOpt = walletRepository.findByUserIdForUpdate(event.userId());
        if (walletOpt.isEmpty()) {
            walletEventPublisher.publish(WalletHoldResultEvent.failed(subscriptionId, event.userId(), null, "WALLET_NOT_FOUND"));
            return;
        }

        Wallet wallet = walletOpt.get();
        long amount = event.payload().amount();

        long balanceBefore = wallet.getBalance();
        try {
            wallet.hold(amount);
        } catch (BusinessException e) {
            walletEventPublisher.publish(WalletHoldResultEvent.failed(subscriptionId, event.userId(), wallet.getId(), "INSUFFICIENT_BALANCE"));
            return;
        } // 예외 X, 결과를 이벤트로만 알림

        walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.HOLD, amount, balanceBefore, wallet.getBalance(),
                "HOLD:" + subscriptionId, subscriptionId.toString()
        ));

        WalletHold hold = walletHoldRepository.save(new WalletHold(wallet.getId(), subscriptionId, amount));

        walletEventPublisher.publish(WalletHoldResultEvent.succeeded(subscriptionId, event.userId(), hold.getId(), wallet.getId()));
    }

    @Transactional
    public void confirmHold(SubscriptionConfirmedEvent event)  // DEDUCT
    {
        UUID subscriptionId = event.aggregateId();

        WalletHold hold = walletHoldRepository.findBySubscriptionId(subscriptionId).orElse(null);
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
    public void compensateHold(SubscriptionCompensationRequestedEvent event) // UNHOLD/REFUND
    {
        UUID subscriptionId = event.aggregateId();

        Optional<WalletHold> holdOpt = walletHoldRepository.findBySubscriptionId(subscriptionId);
        if (holdOpt.isEmpty()) {
            walletEventPublisher.publish(WalletCompensationResultEvent.failed(subscriptionId, event.userId(), null, null, "HOLD_NOT_FOUND"));
            return;
        }

        WalletHold hold = holdOpt.get();
        Wallet wallet = walletRepository.findByUserIdForUpdate(event.userId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        switch (hold.getStatus()) {
            case HELD -> releaseHold(subscriptionId, event.userId(), wallet, hold); // UNHOLD
            case COMMITTED -> refundHold(subscriptionId, event.userId(), wallet, hold); // REFUND
            case RELEASED -> walletEventPublisher.publish(
                    WalletCompensationResultEvent.succeeded(subscriptionId, event.userId(), hold.getId(), wallet.getId(), "NONE", null, null)
            ); // 이미 처리됨
        }
    }

    private void releaseHold(UUID subscriptionId, UUID userId, Wallet wallet, WalletHold hold) {
        long balanceBefore = wallet.getBalance();
        wallet.releaseHold(hold.getAmount());
        hold.release();

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.UNHOLD, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "UNHOLD:" + subscriptionId, subscriptionId.toString()
        ));

        walletEventPublisher.publish(WalletCompensationResultEvent.succeeded(
                subscriptionId, userId, hold.getId(), wallet.getId(), "RELEASE", transaction.getId(), hold.getAmount()));
    }

    private void refundHold(UUID subscriptionId, UUID userId, Wallet wallet, WalletHold hold) {
        long balanceBefore = wallet.getBalance();
        wallet.deposit(hold.getAmount());

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.REFUND, hold.getAmount(), balanceBefore, wallet.getBalance(),
                "REFUND:" + subscriptionId, subscriptionId.toString()
        ));

        walletEventPublisher.publish(WalletCompensationResultEvent.succeeded(
                subscriptionId, userId, hold.getId(), wallet.getId(), "REFUND", transaction.getId(), hold.getAmount()));
    }
}
