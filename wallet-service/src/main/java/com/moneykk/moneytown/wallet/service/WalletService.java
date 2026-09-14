package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.wallet.client.UserServiceClient;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.wallet.dto.response.AdminWalletDetailResponse;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionListItemResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletHoldStatusResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    public AdminWalletDetailResponse getWalletDetail(Long walletId, String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(WalletErrorCode.WALLET_ADMIN_ACCESS_DENIED);
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        return AdminWalletDetailResponse.from(wallet);
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

    // Offering이 관리자 재처리/보상 시 subscriptionId 기준으로 실제 Wallet 처리 상태를 확인하는 내부 조회용
    public WalletHoldStatusResponse getWalletHoldStatus(UUID subscriptionId) {
        WalletHold hold = walletHoldRepository.findBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_HOLD_NOT_FOUND));

        return WalletHoldStatusResponse.from(hold);
    }

    // 클래스 레벨 readOnly 트랜잭션에 합류하면 Feign 호출/UNIQUE 복구가 다시 트랜잭션에 묶인다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse deposit(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), wallet.getId(), WalletTransactionType.DEPOSIT, amount);
        }

        requireEligibleForTransaction(userId);

        try {
            return walletTransactionService.deposit(userId, idempotencyKey, amount);
        } catch (DataIntegrityViolationException e) {
            return recoverFromConcurrentDuplicate(e, wallet.getId(), WalletTransactionType.DEPOSIT, idempotencyKey, amount);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse withdraw(UUID userId, String idempotencyKey, long amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get(), wallet.getId(), WalletTransactionType.WITHDRAW, amount);
        }

        requireEligibleForTransaction(userId);

        try {
            return walletTransactionService.withdraw(userId, idempotencyKey, amount);
        } catch (DataIntegrityViolationException e) {
            return recoverFromConcurrentDuplicate(e, wallet.getId(), WalletTransactionType.WITHDRAW, idempotencyKey, amount);
        }
    }

    // Settlement가 배당 지급 시 호출하는 내부 API. 사용자 요청이 아니라 시스템 간 호출이라
    // KYC/거래가능상태 체크는 하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DividendDepositResponse depositDividend(UUID investorId, String idempotencyKey, UUID settlementBatchId, long amount) {
        Wallet wallet = walletRepository.findByUserId(investorId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildDividendIdempotentResponse(existing.get(), wallet.getId(), amount, settlementBatchId);
        }

        try {
            return walletTransactionService.depositDividend(investorId, idempotencyKey, settlementBatchId, amount);
        } catch (DataIntegrityViolationException e) {
            WalletTransaction winner = walletTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            return buildDividendIdempotentResponse(winner, wallet.getId(), amount, settlementBatchId);
        }
    }

    // Settlement가 자산종료 정산 원금 반환 시 호출하는 내부 API. depositDividend와 동일한 이유로 KYC 체크 없음.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SettlementDepositResponse depositSettlement(UUID investorId, String idempotencyKey, UUID finalSettlementBatchId, long amount) {
        Wallet wallet = walletRepository.findByUserId(investorId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return buildSettlementIdempotentResponse(existing.get(), wallet.getId(), amount, finalSettlementBatchId);
        }

        try {
            return walletTransactionService.depositSettlement(investorId, idempotencyKey, finalSettlementBatchId, amount);
        } catch (DataIntegrityViolationException e) {
            WalletTransaction winner = walletTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            return buildSettlementIdempotentResponse(winner, wallet.getId(), amount, finalSettlementBatchId);
        }
    }

    private DividendDepositResponse buildDividendIdempotentResponse(WalletTransaction existing, Long walletId,
                                                                       long requestedAmount, UUID settlementBatchId) {
        if (!existing.getWalletId().equals(walletId) || existing.getType() != WalletTransactionType.DIVIDEND
                || existing.getAmount() != requestedAmount || !settlementBatchId.toString().equals(existing.getReferenceId())) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        return DividendDepositResponse.from(existing);
    }

    private SettlementDepositResponse buildSettlementIdempotentResponse(WalletTransaction existing, Long walletId,
                                                                          long requestedAmount, UUID finalSettlementBatchId) {
        if (!existing.getWalletId().equals(walletId) || existing.getType() != WalletTransactionType.SETTLEMENT
                || existing.getAmount() != requestedAmount || !finalSettlementBatchId.toString().equals(existing.getReferenceId())) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        return SettlementDepositResponse.from(existing);
    }

    // UNIQUE 제약으로 동시 삽입 승부를 가린 뒤, 진 쪽은 이긴 쪽 결과를 그대로 반환한다.
    private TransactionResponse recoverFromConcurrentDuplicate(DataIntegrityViolationException cause, Long walletId,
                                                                 WalletTransactionType type, String idempotencyKey, long amount) {
        WalletTransaction winner = walletTransactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> cause);

        return buildIdempotentResponse(winner, walletId, type, amount);
    }

    // 지갑/타입/금액까지 같아야 재시도로 인정, 다르면 충돌(다른 유저의 결과 유출 방지).
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
