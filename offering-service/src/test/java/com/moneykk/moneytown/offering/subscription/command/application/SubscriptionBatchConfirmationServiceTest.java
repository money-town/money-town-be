package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private SubscriptionBatchConfirmationService service;

    @Test
    @DisplayName(
            "매진 공모의 모든 청약이 HOLD_SUCCEEDED이면 "
                    + "전체 청약을 확정하고 이벤트를 발행한다"
    )
    void confirmsAllSubscriptionsWhenEveryHoldSucceeded() {
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

        when(firstSubscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);

        when(secondSubscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);

        when(subscriptionRepository
                .findAllReservedByOfferingIdForUpdate(offeringId))
                .thenReturn(List.of(
                        firstSubscription,
                        secondSubscription
                ));

        // when
        int confirmedCount = service.confirmAllIfReady(
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
                        eq(SubscriptionLifecycleMetrics.Result.CONFIRMED),
                        any(Instant.class)
                );

        verify(subscriptionLifecycleMetrics)
                .publishOutcome(
                        eq(secondSubscription),
                        eq(SubscriptionLifecycleMetrics.Result.CONFIRMED),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName(
            "PROCESSING 청약이 하나라도 남아 있으면 "
                    + "청약을 확정하지 않는다"
    )
    void doesNotConfirmWhenProcessingSubscriptionRemains() {
        // given
        UUID offeringId = UUID.randomUUID();
        String correlationId = "correlation-id";

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(subscriptionRepository
                .existsReservedSubscriptionAwaitingHold(offeringId))
                .thenReturn(true);

        // when
        int confirmedCount = service.confirmAllIfReady(
                offering,
                correlationId
        );

        // then
        assertThat(confirmedCount).isZero();

        verify(subscriptionRepository, never())
                .findAllReservedByOfferingIdForUpdate(any(UUID.class));

        verifyNoInteractions(subscriptionEventPublisher);
    }

    @Test
    @DisplayName(
            "이미 CONFIRMED인 청약은 건너뛰고 "
                    + "HOLD_SUCCEEDED 청약만 확정한다"
    )
    void skipsAlreadyConfirmedSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String correlationId = "correlation-id";

        Offering offering =
                mockFinalizableOffering(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        Subscription confirmedSubscription =
                mock(Subscription.class);

        Subscription holdSucceededSubscription =
                mock(Subscription.class);

        when(confirmedSubscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.CONFIRMED);

        when(holdSucceededSubscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);

        when(subscriptionRepository
                .findAllReservedByOfferingIdForUpdate(offeringId))
                .thenReturn(List.of(
                        confirmedSubscription,
                        holdSucceededSubscription
                ));

        // when
        int confirmedCount = service.confirmAllIfReady(
                offering,
                correlationId
        );

        // then
        assertThat(confirmedCount).isEqualTo(1);

        verify(confirmedSubscription, never())
                .confirm(any(Instant.class));

        verify(subscriptionEventPublisher, never())
                .publishConfirmed(
                        eq(confirmedSubscription),
                        eq(assetId),
                        eq(correlationId)
                );

        verify(holdSucceededSubscription)
                .confirm(any(Instant.class));

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        holdSucceededSubscription,
                        assetId,
                        correlationId
                );
    }

    @Test
    @DisplayName(
            "공모가 매진 상태가 아니면 "
                    + "청약 일괄 확정을 시작하지 않는다"
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
        int confirmedCount = service.confirmAllIfReady(
                offering,
                "correlation-id"
        );

        // then
        assertThat(confirmedCount).isZero();

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionEventPublisher
        );
    }

    /**
     * 청약 일괄 확정 조건을 만족하는 매진 공모를 생성한다.
     */
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
