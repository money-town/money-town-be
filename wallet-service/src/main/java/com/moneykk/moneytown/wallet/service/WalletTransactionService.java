package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 실제로 잔액을 잠그고 바꾸는 부분만 여기 모아둔다. WalletService가 멱등성 체크/Feign 호출까지 끝낸 뒤
// 마지막에만 이 클래스를 호출해서, 트랜잭션(=DB 커넥션 점유 구간)이 최대한 짧게 끝나도록 분리했다.
@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public TransactionResponse deposit(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        long balanceBefore = wallet.getBalance();
        wallet.deposit(amount);

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.DEPOSIT, amount,
                balanceBefore, wallet.getBalance(), idempotencyKey, null
        ));

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        long balanceBefore = wallet.getBalance();
        wallet.withdraw(amount);

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.WITHDRAW, amount,
                balanceBefore, wallet.getBalance(), idempotencyKey, null
        ));

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public DividendDepositResponse depositDividend(UUID userId, String idempotencyKey, UUID settlementBatchId, long amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        long balanceBefore = wallet.getBalance();
        wallet.deposit(amount);

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.DIVIDEND, amount,
                balanceBefore, wallet.getBalance(), idempotencyKey, settlementBatchId.toString()
        ));

        return DividendDepositResponse.from(transaction);
    }

    @Transactional
    public SettlementDepositResponse depositSettlement(UUID userId, String idempotencyKey, UUID finalSettlementBatchId, long amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        long balanceBefore = wallet.getBalance();
        wallet.deposit(amount);

        WalletTransaction transaction = walletTransactionRepository.save(new WalletTransaction(
                wallet.getId(), WalletTransactionType.SETTLEMENT, amount,
                balanceBefore, wallet.getBalance(), idempotencyKey, finalSettlementBatchId.toString()
        ));

        return SettlementDepositResponse.from(transaction);
    }
}
