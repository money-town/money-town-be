package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.wallet.client.UserServiceClient;
import com.moneykk.moneytown.wallet.client.dto.UserInvestmentEligibilityResponse;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletHoldRepository walletHoldRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private WalletTransactionService walletTransactionService;

    @InjectMocks
    private WalletService walletService;

    private final UUID investorId = UUID.randomUUID();
    private final UUID settlementBatchId = UUID.randomUUID();

    @Test
    @DisplayName("배당금 입금은 지갑을 찾아 WalletTransactionService에 위임한다")
    void depositDividend_delegatesToTransactionService() {
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        DividendDepositResponse expected = new DividendDepositResponse(10L, 1L, "DIVIDEND", 1_000L, settlementBatchId, null);
        when(walletTransactionService.depositDividend(investorId, "key-1", settlementBatchId, 1_000L)).thenReturn(expected);

        DividendDepositResponse response = walletService.depositDividend(investorId, "key-1", settlementBatchId, 1_000L);

        assertEquals(expected, response);
    }

    @Test
    @DisplayName("같은 멱등키로 이미 처리된 배당금 입금이면 재입금하지 않고 기존 결과를 반환한다")
    void depositDividend_duplicateIdempotencyKey_returnsExistingResult() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = dividendTransaction(1L, 1_000L, settlementBatchId);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        DividendDepositResponse response = walletService.depositDividend(investorId, "key-1", settlementBatchId, 1_000L);

        assertEquals(settlementBatchId, response.settlementBatchId());
        verify(walletTransactionService, never()).depositDividend(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("같은 멱등키인데 배치ID가 다르면 충돌로 처리한다")
    void depositDividend_sameKeyDifferentBatch_throwsConflict() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = dividendTransaction(1L, 1_000L, UUID.randomUUID());
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class,
                () -> walletService.depositDividend(investorId, "key-1", settlementBatchId, 1_000L));
    }

    @Test
    @DisplayName("자산종료 정산 입금은 지갑을 찾아 WalletTransactionService에 위임한다")
    void depositSettlement_delegatesToTransactionService() {
        UUID finalSettlementBatchId = UUID.randomUUID();
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        SettlementDepositResponse expected = new SettlementDepositResponse(11L, 1L, "SETTLEMENT", 5_000L, finalSettlementBatchId, null);
        when(walletTransactionService.depositSettlement(investorId, "key-2", finalSettlementBatchId, 5_000L)).thenReturn(expected);

        SettlementDepositResponse response = walletService.depositSettlement(investorId, "key-2", finalSettlementBatchId, 5_000L);

        assertEquals(expected, response);
    }

    @Test
    @DisplayName("KYC/거래가능상태 요건을 갖춘 사용자의 충전 요청은 WalletTransactionService에 위임한다")
    void deposit_eligibleUser_delegatesToTransactionService() {
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(userServiceClient.getInvestmentEligibility(investorId)).thenReturn(eligibleResponse());
        TransactionResponse expected = TransactionResponse.from(depositTransaction(1L, 1_000L));
        when(walletTransactionService.deposit(investorId, "key-1", 1_000L)).thenReturn(expected);

        TransactionResponse response = walletService.deposit(investorId, "key-1", 1_000L);

        assertEquals(expected, response);
    }

    @Test
    @DisplayName("KYC/거래가능상태 요건을 못 갖췄으면 충전이 거부된다")
    void deposit_ineligibleUser_throwsBusinessException() {
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(userServiceClient.getInvestmentEligibility(investorId)).thenReturn(ineligibleResponse());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.deposit(investorId, "key-1", 1_000L));

        assertEquals(WalletErrorCode.INELIGIBLE_FOR_TRANSACTION, exception.getErrorCode());
        verify(walletTransactionService, never()).deposit(any(), any(), anyLong());
    }

    @Test
    @DisplayName("같은 멱등키로 이미 처리된 충전이면 KYC 검증 없이 기존 결과를 반환한다")
    void deposit_duplicateIdempotencyKey_returnsExistingResult() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = depositTransaction(1L, 1_000L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        TransactionResponse response = walletService.deposit(investorId, "key-1", 1_000L);

        assertEquals(1_000L, response.amount());
        verify(userServiceClient, never()).getInvestmentEligibility(any());
        verify(walletTransactionService, never()).deposit(any(), any(), anyLong());
    }

    @Test
    @DisplayName("같은 멱등키인데 금액이 다르면 충돌로 처리한다")
    void deposit_sameKeyDifferentAmount_throwsConflict() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = depositTransaction(1L, 500L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> walletService.deposit(investorId, "key-1", 1_000L));
    }

    @Test
    @DisplayName("같은 멱등키로 조회된 거래가 다른 지갑 소유면 충돌로 처리한다 (타인 결과 유출 방지)")
    void deposit_sameKeyDifferentWallet_throwsConflict() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = depositTransaction(2L, 1_000L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.deposit(investorId, "key-1", 1_000L));

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    @DisplayName("동시 요청으로 멱등키 UNIQUE 제약을 위반해도, 먼저 처리된 결과를 그대로 반환한다 (동시성 복구)")
    void deposit_concurrentDuplicate_recoversExistingResult() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction winner = depositTransaction(1L, 1_000L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userServiceClient.getInvestmentEligibility(investorId)).thenReturn(eligibleResponse());
        when(walletTransactionService.deposit(investorId, "key-1", 1_000L))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        TransactionResponse response = walletService.deposit(investorId, "key-1", 1_000L);

        assertEquals(1_000L, response.amount());
    }

    @Test
    @DisplayName("KYC/거래가능상태 요건을 갖춘 사용자의 출금 요청은 WalletTransactionService에 위임한다")
    void withdraw_eligibleUser_delegatesToTransactionService() {
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(userServiceClient.getInvestmentEligibility(investorId)).thenReturn(eligibleResponse());
        TransactionResponse expected = TransactionResponse.from(withdrawTransaction(1L, 500L));
        when(walletTransactionService.withdraw(investorId, "key-1", 500L)).thenReturn(expected);

        TransactionResponse response = walletService.withdraw(investorId, "key-1", 500L);

        assertEquals(expected, response);
    }

    @Test
    @DisplayName("출금도 동시 요청으로 멱등키 UNIQUE 제약을 위반하면 먼저 처리된 결과를 그대로 반환한다 (동시성 복구)")
    void withdraw_concurrentDuplicate_recoversExistingResult() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction winner = withdrawTransaction(1L, 500L);
        when(walletRepository.findByUserId(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userServiceClient.getInvestmentEligibility(investorId)).thenReturn(eligibleResponse());
        when(walletTransactionService.withdraw(investorId, "key-1", 500L))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        TransactionResponse response = walletService.withdraw(investorId, "key-1", 500L);

        assertEquals(500L, response.amount());
    }

    private ApiResponse<UserInvestmentEligibilityResponse> eligibleResponse() {
        return ApiResponse.success(new UserInvestmentEligibilityResponse(
                investorId, "ACTIVE", "VERIFIED", Instant.now().plusSeconds(3600)), "ok");
    }

    private ApiResponse<UserInvestmentEligibilityResponse> ineligibleResponse() {
        return ApiResponse.success(new UserInvestmentEligibilityResponse(
                investorId, "ACTIVE", "EXPIRED", Instant.now().minusSeconds(3600)), "ok");
    }

    private WalletTransaction depositTransaction(Long walletId, long amount) {
        WalletTransaction transaction = new WalletTransaction(
                walletId, WalletTransactionType.DEPOSIT, amount, 0L, amount, "key-1", null);
        ReflectionTestUtils.setField(transaction, "id", 20L);
        return transaction;
    }

    private WalletTransaction withdrawTransaction(Long walletId, long amount) {
        WalletTransaction transaction = new WalletTransaction(
                walletId, WalletTransactionType.WITHDRAW, amount, amount, 0L, "key-1", null);
        ReflectionTestUtils.setField(transaction, "id", 21L);
        return transaction;
    }

    private Wallet walletWithId(Long id) {
        Wallet wallet = new Wallet(investorId);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    private WalletTransaction dividendTransaction(Long walletId, long amount, UUID batchId) {
        WalletTransaction transaction = new WalletTransaction(
                walletId, WalletTransactionType.DIVIDEND, amount, 0L, amount, "key-1", batchId.toString());
        ReflectionTestUtils.setField(transaction, "id", 10L);
        return transaction;
    }
}
