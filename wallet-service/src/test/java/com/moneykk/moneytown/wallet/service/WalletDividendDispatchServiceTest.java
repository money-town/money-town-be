package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.consumer.dto.DividendPayoutDispatchPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletTransaction;
import com.moneykk.moneytown.wallet.entity.WalletTransactionType;
import com.moneykk.moneytown.wallet.producer.WalletDividendResultReadyEvent;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletDividendDispatchServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    // .counter(...).increment() 체인만 통과시키면 되므로 deep stub으로 처리
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private WalletDividendDispatchService walletDividendDispatchService;

    private final UUID investorId = UUID.randomUUID();
    private final UUID payoutId = UUID.randomUUID();
    private final UUID settlementBatchId = UUID.randomUUID();

    @Test
    @DisplayName("지갑이 없으면 입금 없이 WALLET_NOT_FOUND 실패 이벤트를 발행한다")
    void processDividendDispatch_walletNotFound_publishesFailedEvent() {
        when(walletRepository.findByUserIdForUpdate(investorId)).thenReturn(Optional.empty());

        walletDividendDispatchService.processDividendDispatch(dispatchEvent(10_000L));

        verify(walletTransactionRepository, never()).save(any());
        ArgumentCaptor<WalletDividendResultReadyEvent> captor = ArgumentCaptor.forClass(WalletDividendResultReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("DividendPayoutDispatchFailed", captor.getValue().event().eventType());
        assertEquals("WALLET_NOT_FOUND", captor.getValue().event().payload().reason());
    }

    @Test
    @DisplayName("정상 요청이면 입금 후 성공 이벤트를 발행한다")
    void processDividendDispatch_success_publishesSucceededEvent() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction saved = dividendTransaction(1L, 10_000L);
        when(walletRepository.findByUserIdForUpdate(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey(payoutId.toString())).thenReturn(Optional.empty());
        when(walletTransactionRepository.save(any())).thenReturn(saved);

        walletDividendDispatchService.processDividendDispatch(dispatchEvent(10_000L));

        assertEquals(10_000L, wallet.getBalance());
        ArgumentCaptor<WalletDividendResultReadyEvent> captor = ArgumentCaptor.forClass(WalletDividendResultReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("DividendPayoutDispatchSucceeded", captor.getValue().event().eventType());
        assertEquals(saved.getId(), captor.getValue().event().payload().transactionId());
    }

    @Test
    @DisplayName("이미 처리된 payoutId면 입금을 다시 시도하지 않고 기존 거래로 성공 이벤트를 발행한다 (멱등)")
    void processDividendDispatch_alreadyProcessed_isIdempotent() {
        Wallet wallet = walletWithId(1L);
        WalletTransaction existing = dividendTransaction(1L, 10_000L);
        when(walletRepository.findByUserIdForUpdate(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey(payoutId.toString())).thenReturn(Optional.of(existing));

        walletDividendDispatchService.processDividendDispatch(dispatchEvent(10_000L));

        assertEquals(0L, wallet.getBalance()); // 재입금되지 않았어야 함
        verify(walletTransactionRepository, never()).save(any());
        ArgumentCaptor<WalletDividendResultReadyEvent> captor = ArgumentCaptor.forClass(WalletDividendResultReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("DividendPayoutDispatchSucceeded", captor.getValue().event().eventType());
        assertEquals(existing.getId(), captor.getValue().event().payload().transactionId());
    }

    @Test
    @DisplayName("입금 금액이 잘못되면(0 이하) 저장 없이 실패 이벤트를 발행한다")
    void processDividendDispatch_invalidAmount_publishesFailedEvent() {
        Wallet wallet = walletWithId(1L);
        when(walletRepository.findByUserIdForUpdate(investorId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByIdempotencyKey(payoutId.toString())).thenReturn(Optional.empty());

        walletDividendDispatchService.processDividendDispatch(dispatchEvent(0L));

        verify(walletTransactionRepository, never()).save(any());
        ArgumentCaptor<WalletDividendResultReadyEvent> captor = ArgumentCaptor.forClass(WalletDividendResultReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertEquals("INVALID_AMOUNT", captor.getValue().event().payload().reason());
    }

    private Wallet walletWithId(Long id) {
        Wallet wallet = new Wallet(investorId);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    private WalletTransaction dividendTransaction(Long id, long amount) {
        WalletTransaction transaction = new WalletTransaction(
                1L, WalletTransactionType.DIVIDEND, amount, 0L, amount,
                payoutId.toString(), settlementBatchId.toString());
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private EventEnvelope<DividendPayoutDispatchPayload> dispatchEvent(long amount) {
        return EventEnvelope.of("DividendPayoutDispatchRequested", payoutId.toString(), null, "corr-1",
                new DividendPayoutDispatchPayload(payoutId, settlementBatchId, investorId, amount));
    }
}
