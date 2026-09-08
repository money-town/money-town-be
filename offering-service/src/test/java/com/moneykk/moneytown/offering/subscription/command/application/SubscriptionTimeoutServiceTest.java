package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @InjectMocks
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @Test
    @DisplayName("만료된 청약을 보상 상태로 전환하고 Wallet 보상 요청을 저장한다")
    void processesExpiredReservations() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId1 = UUID.randomUUID();
        UUID subscriptionId2 = UUID.randomUUID();

        Subscription subscription1 = mockSubscription(
                offeringId,
                subscriptionId1
        );
        Subscription subscription2 = mockSubscription(
                offeringId,
                subscriptionId2
        );

        Offering offering = mock(Offering.class);

        when(subscriptionRepository
                .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(Instant.class),
                        any()
                ))
                .thenReturn(List.of(
                        subscription1,
                        subscription2
                ));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(offering.getAssetId()).thenReturn(assetId);

        // when
        int result =
                subscriptionTimeoutService.processExpiredReservations();

        // then
        assertThat(result).isEqualTo(2);

        verify(subscription1)
                .startExpirationCompensation(any(Instant.class));
        verify(subscription2)
                .startExpirationCompensation(any(Instant.class));

        ArgumentCaptor<SubscriptionCompensation>
                compensationCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionCompensation.class
                );

        verify(subscriptionCompensationRepository, times(2))
                .save(compensationCaptor.capture());

        List<SubscriptionCompensation> compensations =
                compensationCaptor.getAllValues();

        assertThat(compensations)
                .extracting(
                        SubscriptionCompensation::getSubscriptionId
                )
                .containsExactly(
                        subscriptionId1,
                        subscriptionId2
                );

        assertThat(compensations)
                .allSatisfy(compensation -> {
                    assertThat(compensation.getWalletStatus())
                            .isEqualTo(CompensationStatus.PENDING);
                    assertThat(compensation.getHoldingStatus())
                            .isEqualTo(CompensationStatus.SUCCEEDED);
                    assertThat(
                            compensation
                                    .isExternalCompensationCompleted()
                    ).isFalse();
                });

        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        eq(subscription1),
                        eq(assetId),
                        correlationIdCaptor.capture()
                );

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        eq(subscription2),
                        eq(assetId),
                        correlationIdCaptor.capture()
                );

        assertThat(correlationIdCaptor.getAllValues())
                .hasSize(2)
                .allSatisfy(correlationId ->
                        assertThat(correlationId).isNotBlank()
                );
    }

    @Test
    @DisplayName("예약 유효시간이 만료된 청약이 없으면 처리 건수 0을 반환한다")
    void returnsZeroWhenNoExpiredReservations() {
        // given
        when(subscriptionRepository
                .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(Instant.class),
                        any()
                ))
                .thenReturn(List.of());

        // when
        int result =
                subscriptionTimeoutService.processExpiredReservations();

        // then
        assertThat(result).isZero();

        verifyNoInteractions(
                offeringRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("청약 대상 공모를 찾을 수 없으면 보상 처리를 시작하지 않는다")
    void doesNotStartCompensationWhenOfferingDoesNotExist() {
        // given
        UUID offeringId = UUID.randomUUID();

        Subscription subscription = mock(Subscription.class);

        when(subscription.getOfferingId())
                .thenReturn(offeringId);

        when(subscriptionRepository
                .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(Instant.class),
                        any()
                ))
                .thenReturn(List.of(subscription));

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> subscriptionTimeoutService
                        .processExpiredReservations()
        )
                .isInstanceOf(BusinessException.class);

        verify(subscription, never())
                .startExpirationCompensation(any(Instant.class));

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    private Subscription mockSubscription(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Subscription subscription = mock(Subscription.class);

        when(subscription.getOfferingId())
                .thenReturn(offeringId);
        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        return subscription;
    }
}