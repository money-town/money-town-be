package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutBatchTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionCompensationRepository compensationRepository;
    @Mock
    private SubscriptionEventPublisher eventPublisher;
    @Mock
    private Offering offering;
    @Mock
    private Subscription firstSubscription;
    @Mock
    private Subscription secondSubscription;

    private SubscriptionTimeoutBatchTransactionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionTimeoutBatchTransactionService(
                offeringRepository,
                subscriptionRepository,
                compensationRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("만료 청약이 있는 공모가 없으면 처리하지 않는다")
    void returnsZeroWhenNoOfferingTargetExists() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        when(offeringRepository
                .findNextExpiredReservationTargetForUpdate(now))
                .thenReturn(Optional.empty());

        int result = service.processNextBatch(now, 100);

        assertThat(result).isZero();
        verifyNoInteractions(
                subscriptionRepository,
                compensationRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("선점한 공모의 만료 청약을 한 배치로 보상 처리한다")
    void processesClaimedOfferingBatch() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getAssetId()).thenReturn(assetId);
        when(firstSubscription.getSubscriptionId()).thenReturn(firstId);
        when(secondSubscription.getSubscriptionId()).thenReturn(secondId);
        when(offeringRepository
                .findNextExpiredReservationTargetForUpdate(now))
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository
                .findExpiredProcessingBatchForUpdate(
                        offeringId, now, 100))
                .thenReturn(List.of(
                        firstSubscription,
                        secondSubscription
                ));

        int result = service.processNextBatch(now, 100);

        assertThat(result).isEqualTo(2);
        verify(firstSubscription).startExpirationCompensation(now);
        verify(secondSubscription).startExpirationCompensation(now);
        verify(compensationRepository, times(2))
                .save(any(SubscriptionCompensation.class));
        verify(compensationRepository).flush();
        verify(eventPublisher).publishCompensationRequested(
                eq(firstSubscription), eq(assetId), any(String.class));
        verify(eventPublisher).publishCompensationRequested(
                eq(secondSubscription), eq(assetId), any(String.class));
    }

    @Test
    @DisplayName("공모 선점 후 만료 대상이 없으면 처리하지 않는다")
    void returnsZeroWhenClaimedOfferingHasNoTarget() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        UUID offeringId = UUID.randomUUID();

        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offeringRepository
                .findNextExpiredReservationTargetForUpdate(now))
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository
                .findExpiredProcessingBatchForUpdate(
                        offeringId, now, 100))
                .thenReturn(List.of());

        int result = service.processNextBatch(now, 100);

        assertThat(result).isZero();
        verifyNoInteractions(compensationRepository, eventPublisher);
    }

    @Test
    @DisplayName("배치 저장 실패 시 공모와 청약 ID를 담은 예외로 변환한다")
    void wrapsBatchFailureWithTargetIds() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getAssetId()).thenReturn(assetId);
        when(firstSubscription.getSubscriptionId())
                .thenReturn(subscriptionId);
        when(offeringRepository
                .findNextExpiredReservationTargetForUpdate(now))
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository
                .findExpiredProcessingBatchForUpdate(
                        offeringId, now, 100))
                .thenReturn(List.of(firstSubscription));

        doThrow(new IllegalStateException("Outbox 저장 실패"))
                .when(eventPublisher)
                .publishCompensationRequested(
                        eq(firstSubscription),
                        eq(assetId),
                        any(String.class)
                );

        assertThatThrownBy(() -> service.processNextBatch(now, 100))
                .isInstanceOf(SubscriptionTimeoutBatchException.class)
                .satisfies(throwable -> {
                    SubscriptionTimeoutBatchException exception =
                            (SubscriptionTimeoutBatchException) throwable;

                    assertThat(exception.getOfferingId())
                            .isEqualTo(offeringId);
                    assertThat(exception.getSubscriptionIds())
                            .containsExactly(subscriptionId);
                });

        verify(compensationRepository, never()).flush();
    }
}
