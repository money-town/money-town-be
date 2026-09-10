package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.dto.response.DividendDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.SettlementDepositResponse;
import com.moneykk.moneytown.wallet.dto.response.TransactionResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    // 실제 카운터 증가가 맞게 기록되는지 확인하려고 mock 대신 진짜 레지스트리를 쓴다.
    private SimpleMeterRegistry meterRegistry;
    private WalletTransactionService walletTransactionService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        walletTransactionService = new WalletTransactionService(walletRepository, walletTransactionRepository, meterRegistry);
        lenient().when(walletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("충전하면 잔액이 늘고 DEPOSIT 거래가 기록되고 카운터가 증가한다")
    void deposit_success() {
        Wallet wallet = walletWithId(1L);

        TransactionResponse response = walletTransactionService.deposit(userId, "key-1", 1_000L);

        assertEquals(1L, response.walletId());
        assertEquals(WalletTransactionType.DEPOSIT, response.type());
        assertEquals(1_000L, response.amount());
        assertEquals(1_000L, response.balanceAfter());
        assertEquals(1_000L, wallet.getBalance());
        assertEquals(1.0, counterCount(WalletTransactionType.DEPOSIT));
    }

    @Test
    @DisplayName("지갑이 없으면 카운터 증가 없이 404를 반환한다")
    void deposit_walletNotFound_throwsBusinessException() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletTransactionService.deposit(userId, "key-1", 1_000L));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verify(walletTransactionRepository, never()).save(any());
        assertEquals(0.0, counterCount(WalletTransactionType.DEPOSIT));
    }

    @Test
    @DisplayName("잔액이 Long 최댓값을 넘기면 저장·카운터 증가 없이 오버플로 예외를 던진다")
    void deposit_overflow_throwsBusinessExceptionWithoutSideEffects() {
        Wallet wallet = walletWithId(1L);
        ReflectionTestUtils.setField(wallet, "balance", Long.MAX_VALUE);
        ReflectionTestUtils.setField(wallet, "availableBalance", Long.MAX_VALUE);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletTransactionService.deposit(userId, "key-1", 1L));

        assertEquals(WalletErrorCode.BALANCE_OVERFLOW, exception.getErrorCode());
        verify(walletTransactionRepository, never()).save(any());
        assertEquals(0.0, counterCount(WalletTransactionType.DEPOSIT));
    }

    @Test
    @DisplayName("출금하면 잔액이 줄고 WITHDRAW 거래가 기록되고 카운터가 증가한다")
    void withdraw_success() {
        Wallet wallet = walletWithId(1L);
        wallet.deposit(5_000L);

        TransactionResponse response = walletTransactionService.withdraw(userId, "key-1", 2_000L);

        assertEquals(WalletTransactionType.WITHDRAW, response.type());
        assertEquals(2_000L, response.amount());
        assertEquals(3_000L, response.balanceAfter());
        assertEquals(3_000L, wallet.getBalance());
        assertEquals(1.0, counterCount(WalletTransactionType.WITHDRAW));
    }

    @Test
    @DisplayName("가용잔액이 부족하면 저장·카운터 증가 없이 예외를 던진다")
    void withdraw_insufficientBalance_throwsBusinessExceptionWithoutSideEffects() {
        Wallet wallet = walletWithId(1L);
        wallet.deposit(100L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletTransactionService.withdraw(userId, "key-1", 1_000L));

        assertEquals(WalletErrorCode.INSUFFICIENT_AVAILABLE_BALANCE, exception.getErrorCode());
        verify(walletTransactionRepository, never()).save(any());
        assertEquals(0.0, counterCount(WalletTransactionType.WITHDRAW));
    }

    @Test
    @DisplayName("배당금 입금은 지갑 잔액에 반영되고 정산배치ID가 참조ID로 기록되고 카운터가 증가한다")
    void depositDividend_success() {
        Wallet wallet = walletWithId(1L);
        UUID settlementBatchId = UUID.randomUUID();

        DividendDepositResponse response = walletTransactionService.depositDividend(userId, "key-1", settlementBatchId, 3_000L);

        assertEquals("DIVIDEND", response.type());
        assertEquals(3_000L, response.amount());
        assertEquals(settlementBatchId, response.settlementBatchId());
        assertEquals(3_000L, wallet.getBalance());
        assertEquals(1.0, counterCount(WalletTransactionType.DIVIDEND));
    }

    @Test
    @DisplayName("자산종료 정산 입금은 지갑 잔액에 반영되고 최종정산배치ID가 참조ID로 기록되고 카운터가 증가한다")
    void depositSettlement_success() {
        Wallet wallet = walletWithId(1L);
        UUID finalSettlementBatchId = UUID.randomUUID();

        SettlementDepositResponse response = walletTransactionService.depositSettlement(userId, "key-1", finalSettlementBatchId, 7_000L);

        assertEquals("SETTLEMENT", response.type());
        assertEquals(7_000L, response.amount());
        assertEquals(finalSettlementBatchId, response.finalSettlementBatchId());
        assertEquals(7_000L, wallet.getBalance());
        assertEquals(1.0, counterCount(WalletTransactionType.SETTLEMENT));
    }

    private double counterCount(WalletTransactionType type) {
        var counter = meterRegistry.find("wallet.transaction.count").tag("type", type.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Wallet walletWithId(Long id) {
        Wallet wallet = new Wallet(userId);
        ReflectionTestUtils.setField(wallet, "id", id);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        return wallet;
    }
}
