package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.client.UserServiceClient;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
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
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserServiceClient userServiceClient;

    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        return WalletResponse.from(wallet);
    }

    public WalletStatusResponse getWalletStatus(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        boolean hasActiveHold = walletHoldRepository.existsByWalletIdAndStatus(wallet.getId(), WalletHoldStatus.HELD);

        return WalletStatusResponse.of(wallet, hasActiveHold);
    }

    @Transactional
    public TransactionResponse deposit(UUID userId, String idempotencyKey, long amount) {
        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), WalletTransactionType.DEPOSIT, amount);
        }

        requireEligibleForTransaction(userId);

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
        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), WalletTransactionType.WITHDRAW, amount);
        }

        requireEligibleForTransaction(userId);

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

    // 동일 idempotencyKey로 이미 처리된 거래가 있을 때: 요청 내용(타입+금액)이 똑같으면 그 결과를 그대로
    // 재반환하고(재시도 허용), 하나라도 다르면 이 키가 이미 다른 의미로 쓰였다는 뜻이므로 충돌로 처리한다.
    private TransactionResponse buildIdempotentResponse(WalletTransaction existing, WalletTransactionType type, long requestedAmount) {
        if (existing.getType() != type || existing.getAmount() != requestedAmount) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        return TransactionResponse.from(existing);
    }

    private void requireEligibleForTransaction(UUID userId) {
        ApiResponse<UserInvestmentEligibilityResponse> response = userServiceClient.getInvestmentEligibility(userId);
        UserInvestmentEligibilityResponse eligibility = response.data();

        if (eligibility == null || !eligibility.isEligibleForTransaction()) {
            throw new BusinessException(WalletErrorCode.INELIGIBLE_FOR_TRANSACTION);
        }
    }
}
