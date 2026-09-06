package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.client.UserServiceClient;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
