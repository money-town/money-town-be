package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHold;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.producer.WalletEventPublisher;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletHoldServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletHoldRepository walletHoldRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private WalletEventPublisher walletEventPublisher;

    @InjectMocks
    private WalletHoldService walletHoldService;

    private final UUID userId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    @Test
    @DisplayName("가용잔액이 부족하면 동결 없이 실패 이벤트만 발행한다")
    void processReservation_insufficientBalance_publishesFailedEvent() {
        Wallet wallet = walletWithId(1L, 0L);
        when(walletHoldRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        walletHoldService.processReservation(reservedEvent(1_000L));

        verify(walletHoldRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
        ArgumentCaptor<EventEnvelope<WalletHoldResultPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(walletEventPublisher).publishHoldResult(captor.capture());
        assertEquals("WalletHoldFailed", captor.getValue().eventType());
        assertEquals("INSUFFICIENT_BALANCE", captor.getValue().payload().reason());
    }

    @Test
    @DisplayName("이미 처리된 구독이면 지갑 조회 없이 조용히 종료한다 (멱등)")
    void processReservation_duplicateSubscription_isIdempotent() {
        when(walletHoldRepository.findBySubscriptionId(subscriptionId))
                .thenReturn(Optional.of(walletHoldWithId(1L, 1L, subscriptionId, 1_000L)));

        walletHoldService.processReservation(reservedEvent(1_000L));

        verifyNoInteractions(walletRepository, walletTransactionRepository, walletEventPublisher);
    }

    @Test
    @DisplayName("Hold가 이미 COMMITTED면 지갑 조회 없이 조용히 종료한다 (재차감 방지)")
    void confirmHold_alreadyCommitted_isIdempotent() {
        WalletHold hold = walletHoldWithId(1L, 1L, subscriptionId, 1_000L);
        hold.commit();
        when(walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId)).thenReturn(Optional.of(hold));

        walletHoldService.confirmHold(confirmedEvent());

        verifyNoInteractions(walletRepository, walletTransactionRepository, walletEventPublisher);
    }

    @Test
    @DisplayName("HELD 상태의 보상 요청은 동결을 해제(UNHOLD)한다")
    void compensateHold_held_releasesHold() {
        Wallet wallet = walletWithId(1L, 1_000L);
        wallet.hold(1_000L);
        WalletHold hold = walletHoldWithId(1L, wallet.getId(), subscriptionId, 1_000L);
        when(walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId)).thenReturn(Optional.of(hold));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        walletHoldService.compensateHold(compensationEvent());

        assertEquals(0L, wallet.getHoldBalance());
        assertEquals(1_000L, wallet.getAvailableBalance());
        verify(walletTransactionRepository).save(any());
        ArgumentCaptor<EventEnvelope<WalletCompensationResultPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(walletEventPublisher).publishCompensationResult(captor.capture());
        assertEquals("WalletCompensationSucceeded", captor.getValue().eventType());
        assertEquals("RELEASE", captor.getValue().payload().compensationType());
    }

    @Test
    @DisplayName("COMMITTED 상태의 보상 요청은 원금을 환불(REFUND)한다")
    void compensateHold_committed_refundsAmount() {
        Wallet wallet = walletWithId(1L, 1_000L);
        wallet.hold(1_000L);
        wallet.deductHold(1_000L);
        WalletHold hold = walletHoldWithId(1L, wallet.getId(), subscriptionId, 1_000L);
        hold.commit();
        when(walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId)).thenReturn(Optional.of(hold));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        walletHoldService.compensateHold(compensationEvent());

        assertEquals(1_000L, wallet.getBalance());
        assertEquals(WalletHoldStatus.REFUNDED, hold.getStatus());
        ArgumentCaptor<EventEnvelope<WalletCompensationResultPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(walletEventPublisher).publishCompensationResult(captor.capture());
        assertEquals("REFUND", captor.getValue().payload().compensationType());
    }

    @Test
    @DisplayName("이미 환불된 보상 요청이 재전송돼도 잔액을 다시 건드리지 않는다 (멱등)")
    void compensateHold_alreadyRefunded_doesNotDoubleRefund() {
        Wallet wallet = walletWithId(1L, 1_000L);
        wallet.hold(1_000L);
        wallet.deductHold(1_000L);
        WalletHold hold = walletHoldWithId(1L, wallet.getId(), subscriptionId, 1_000L);
        hold.commit();
        hold.refund();
        when(walletHoldRepository.findBySubscriptionIdForUpdate(subscriptionId)).thenReturn(Optional.of(hold));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        walletHoldService.compensateHold(compensationEvent());

        assertEquals(0L, wallet.getBalance());
        verify(walletTransactionRepository, never()).save(any());
        ArgumentCaptor<EventEnvelope<WalletCompensationResultPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(walletEventPublisher).publishCompensationResult(captor.capture());
        assertEquals("NONE", captor.getValue().payload().compensationType());
    }

    private Wallet walletWithId(Long id, long depositAmount) {
        Wallet wallet = new Wallet(userId);
        ReflectionTestUtils.setField(wallet, "id", id);
        if (depositAmount > 0) {
            wallet.deposit(depositAmount);
        }
        return wallet;
    }

    private WalletHold walletHoldWithId(Long id, Long walletId, UUID subscriptionId, long amount) {
        WalletHold hold = new WalletHold(walletId, subscriptionId, amount);
        ReflectionTestUtils.setField(hold, "id", id);
        return hold;
    }

    private EventEnvelope<SubscriptionReservedPayload> reservedEvent(long amount) {
        return EventEnvelope.of("SubscriptionReserved", subscriptionId.toString(), userId, "corr-1",
                new SubscriptionReservedPayload(amount));
    }

    private EventEnvelope<Object> confirmedEvent() {
        return EventEnvelope.of("SubscriptionConfirmed", subscriptionId.toString(), userId, "corr-1", null);
    }

    private EventEnvelope<SubscriptionCompensationRequestedPayload> compensationEvent() {
        return EventEnvelope.of("SubscriptionCompensationRequested", subscriptionId.toString(), userId, "corr-1",
                new SubscriptionCompensationRequestedPayload("OFFERING_UNDERFILLED"));
    }
}
