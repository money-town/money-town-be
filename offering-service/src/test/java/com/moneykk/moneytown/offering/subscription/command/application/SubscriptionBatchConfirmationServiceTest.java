package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionConfirmationProperties;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionBatchConfirmationMetrics;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionBatchConfirmationServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Mock
    private SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @Mock
    private SubscriptionBatchConfirmationMetrics
            subscriptionBatchConfirmationMetrics;

    @Spy
    private SubscriptionConfirmationProperties confirmationProperties =
            new SubscriptionConfirmationProperties();

    @InjectMocks
    private SubscriptionBatchConfirmationService service;

    @Test
    @DisplayName(
            "모든 Wallet HOLD가 완료되면 "
                    + "HOLD_SUCCEEDED 청약 한 배치를 확정한다"
    )
    void confirmsNextSubscriptionBatchWhenEveryHoldCompleted() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String correlationId = "correlation-id";

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        Subscription firstSubscription =
                mock(Subscription.class);

        Subscription secondSubscription =
                mock(Subscription.class);

        when(subscriptionRepository
                .findHoldSucceededBatchForUpdate(
                        eq(offeringId),
                        eq(PageRequest.of(0, 100))
                ))
                .thenReturn(List.of(
                        firstSubscription,
                        secondSubscription
                ));

        // when
        int confirmedCount =
                service.confirmNextBatchIfReady(
                        offering,
                        correlationId
                );

        // then
        assertThat(confirmedCount).isEqualTo(2);

        verify(firstSubscription)
                .confirm(any(Instant.class));

        verify(secondSubscription)
                .confirm(any(Instant.class));

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        firstSubscription,
                        assetId,
                        correlationId
                );

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        secondSubscription,
                        assetId,
                        correlationId
                );

        verify(subscriptionLifecycleMetrics)
                .publishOutcome(
                        eq(firstSubscription),
                        eq(
                                SubscriptionLifecycleMetrics
                                        .Result.CONFIRMED
                        ),
                        any(Instant.class)
                );

        verify(subscriptionLifecycleMetrics)
                .publishOutcome(
                        eq(secondSubscription),
                        eq(
                                SubscriptionLifecycleMetrics
                                        .Result.CONFIRMED
                        ),
                        any(Instant.class)
                );

        verify(subscriptionBatchConfirmationMetrics)
                .publish(
                        any(Duration.class),
                        eq(2)
                );
    }

    @Test
    @DisplayName(
            "PROCESSING 청약이 하나라도 남아 있으면 "
                    + "확정 배치를 조회하지 않는다"
    )
    void doesNotConfirmWhenProcessingSubscriptionRemains() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = "correlation-id";

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(subscriptionRepository
                .existsReservedSubscriptionAwaitingHold(
                        offeringId
                ))
                .thenReturn(true);

        // when
        int confirmedCount =
                service.confirmNextBatchIfReady(
                        offering,
                        correlationId
                );

        // then
        assertThat(confirmedCount).isZero();

        verify(subscriptionRepository, never())
                .findHoldSucceededBatchForUpdate(
                        any(UUID.class),
                        any(Pageable.class)
                );

        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics,
                subscriptionBatchConfirmationMetrics
        );
    }

    @Test
    @DisplayName(
            "확정할 HOLD_SUCCEEDED 청약이 남아 있지 않으면 "
                    + "이벤트를 생성하지 않는다"
    )
    void returnsZeroWhenNoHoldSucceededSubscriptionRemains() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = "correlation-id";

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(subscriptionRepository
                .findHoldSucceededBatchForUpdate(
                        eq(offeringId),
                        eq(PageRequest.of(0, 100))
                ))
                .thenReturn(List.of());

        // when
        int confirmedCount =
                service.confirmNextBatchIfReady(
                        offering,
                        correlationId
                );

        // then
        assertThat(confirmedCount).isZero();

        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics,
                subscriptionBatchConfirmationMetrics
        );
    }

    @Test
    @DisplayName(
            "공모가 최종 확정 조건을 만족하지 않으면 "
                    + "청약 확정을 시작하지 않는다"
    )
    void doesNotConfirmWhenOfferingIsNotFinalizable() {
        // given
        UUID offeringId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.OPEN);

        when(offering.getRemainingQuantity())
                .thenReturn(10L);

        // when
        int confirmedCount =
                service.confirmNextBatchIfReady(
                        offering,
                        "correlation-id"
                );

        // then
        assertThat(confirmedCount).isZero();

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionEventPublisher,
                subscriptionLifecycleMetrics,
                subscriptionBatchConfirmationMetrics
        );
    }

    @Test
    @DisplayName(
            "flush에서 확정 배치가 실패하면 "
                    + "공모와 청약 ID를 예외에 보존한다"
    )
    void preservesBatchTargetsWhenFlushFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(offering.getAssetId())
                .thenReturn(UUID.randomUUID());

        Subscription subscription =
                mock(Subscription.class);

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository
                .findHoldSucceededBatchForUpdate(
                        eq(offeringId),
                        eq(PageRequest.of(0, 100))
                ))
                .thenReturn(List.of(subscription));

        doThrow(new org.springframework.dao.DataIntegrityViolationException(
                "flush failure"
        ))
                .when(subscriptionRepository)
                .flush();

        // when & then
        assertThatThrownBy(
                () -> service.confirmNextBatchIfReady(
                        offering,
                        "correlation-id"
                )
        )
                .isInstanceOf(
                        SubscriptionConfirmationBatchException.class
                )
                .satisfies(throwable -> {
                    SubscriptionConfirmationBatchException exception =
                            (SubscriptionConfirmationBatchException)
                                    throwable;

                    assertThat(exception.getOfferingId())
                            .isEqualTo(offeringId);

                    assertThat(exception.getSubscriptionIds())
                            .containsExactly(subscriptionId);
                });

        verify(subscriptionBatchConfirmationMetrics, never())
                .publish(any(Duration.class), any(Integer.class));
    }
    private Offering mockFinalizableOffering(
            UUID offeringId
    ) {
        Offering offering = mock(Offering.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.SOLD_OUT);

        when(offering.getRemainingQuantity())
                .thenReturn(0L);

        return offering;
    }
}
