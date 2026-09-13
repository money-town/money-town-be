package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @InjectMocks
    private SubscriptionTimeoutTransactionService
            subscriptionTimeoutTransactionService;

    @Test
    @DisplayName("예약이 만료된 청약을 보상 상태로 전환하고 Outbox 이벤트를 저장한다")
    void startsExpirationCompensation() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Instant reservationExpiresAt =
                Instant.now().plusSeconds(60);

        Instant processingTime =
                reservationExpiresAt.plusSeconds(1);

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                reservationExpiresAt
        );

        Offering offering = mock(Offering.class);

        when(subscriptionRepository
                .findOfferingIdBySubscriptionId(
                        subscription.getSubscriptionId()
                ))
                .thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.findByIdForUpdate(
                subscription.getSubscriptionId()
        ))
                .thenReturn(Optional.of(subscription));

        when(offering.getAssetId())
                .thenReturn(assetId);

        // when
        boolean result =
                subscriptionTimeoutTransactionService
                        .processExpiredReservation(
                                subscription.getSubscriptionId(),
                                processingTime
                        );

        // then
        assertThat(result).isTrue();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");

        /*
         * 공모 → 청약 순서로 잠금을 획득하는지 검증한다.
         */
        InOrder lockOrder = inOrder(
                subscriptionRepository,
                offeringRepository
        );

        lockOrder.verify(subscriptionRepository)
                .findOfferingIdBySubscriptionId(
                        subscription.getSubscriptionId()
                );

        lockOrder.verify(offeringRepository)
                .findByIdForUpdate(offeringId);

        lockOrder.verify(subscriptionRepository)
                .findByIdForUpdate(
                        subscription.getSubscriptionId()
                );

        ArgumentCaptor<SubscriptionCompensation>
                compensationCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionCompensation.class
                );

        verify(subscriptionCompensationRepository)
                .save(compensationCaptor.capture());

        SubscriptionCompensation compensation =
                compensationCaptor.getValue();

        assertThat(compensation.getSubscriptionId())
                .isEqualTo(subscription.getSubscriptionId());

        /*
         * 예약 만료는 Wallet 보상만 필요하므로
         * Wallet은 PENDING, Holding은 SUCCEEDED로 시작한다.
         */
        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.PENDING);

        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        eq(subscription),
                        eq(assetId),
                        correlationIdCaptor.capture()
                );

        assertThat(correlationIdCaptor.getValue())
                .isNotBlank();
    }

    @Test
    @DisplayName("잠금 대기 중 Wallet HOLD가 성공한 청약은 만료 처리하지 않는다")
    void skipsSubscriptionChangedAfterCandidateSelection() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Instant reservationExpiresAt =
                Instant.now().plusSeconds(60);

        Instant processingTime =
                reservationExpiresAt.plusSeconds(1);

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                reservationExpiresAt
        );

        /*
         * 만료 대상 ID 조회 이후 Wallet 성공 이벤트가 먼저 처리된 상황이다.
         */
        subscription.markHoldSucceeded();

        Offering offering = mock(Offering.class);

        when(subscriptionRepository
                .findOfferingIdBySubscriptionId(
                        subscription.getSubscriptionId()
                ))
                .thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        when(subscriptionRepository.findByIdForUpdate(
                subscription.getSubscriptionId()
        ))
                .thenReturn(Optional.of(subscription));

        // when
        boolean result =
                subscriptionTimeoutTransactionService
                        .processExpiredReservation(
                                subscription.getSubscriptionId(),
                                processingTime
                        );

        // then
        assertThat(result).isFalse();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.HOLD_SUCCEEDED);

        assertThat(subscription.getFailureCode())
                .isNull();

        verify(subscriptionCompensationRepository, never())
                .save(org.mockito.ArgumentMatchers.any());

        verifyNoInteractions(subscriptionEventPublisher);
    }
}