package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.wallet.client.UserServiceClient;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionListItemResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final WalletTransactionService walletTransactionService;

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

    public PageResponse<TransactionListItemResponse> getTransactions(UUID userId, WalletTransactionType type, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Page<WalletTransaction> transactions = walletTransactionRepository.findByWalletId(wallet.getId(), type, pageable);

        return PageResponse.from(transactions, TransactionListItemResponse::from);
    }

    public TransactionResponse deposit(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), wallet.getId(), WalletTransactionType.DEPOSIT, amount);
        }

        requireEligibleForTransaction(userId);

        return walletTransactionService.deposit(userId, idempotencyKey, amount);
    }

    public TransactionResponse withdraw(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), wallet.getId(), WalletTransactionType.WITHDRAW, amount);
        }

        requireEligibleForTransaction(userId);

        return walletTransactionService.withdraw(userId, idempotencyKey, amount);
    }

    // 동일 idempotencyKey로 이미 처리된 거래가 있을 때: 그 거래가 "내 지갑" 것이고 타입+금액까지 똑같으면
    // 그 결과를 그대로 재반환하고(재시도 허용), 지갑이 다르거나 타입/금액이 다르면 충돌로 처리한다.
    // walletId까지 확인하는 이유는, 다른 유저가 우연히 같은 idempotencyKey를 썼을 때
    // 그 유저의 거래 정보(잔액 등)가 그대로 반환되는 걸 막기 위해서다.
    private TransactionResponse buildIdempotentResponse(WalletTransaction existing, Long walletId,
                                                          WalletTransactionType type, long requestedAmount) {
        if (!existing.getWalletId().equals(walletId) || existing.getType() != type || existing.getAmount() != requestedAmount) {
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
