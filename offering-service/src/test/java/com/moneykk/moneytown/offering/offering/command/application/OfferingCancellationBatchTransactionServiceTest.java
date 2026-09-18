package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCancellationBatchTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository subscriptionCompensationRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @InjectMocks
    private OfferingCancellationBatchTransactionService service;

    @Test
    @DisplayName("취소 처리 대상 공모가 없으면 아무 청약도 처리하지 않는다")
    void returnsZeroWhenCancellationTargetDoesNotExist() {
        // given
        when(
                offeringRepository
                        .findNextCancellationTargetForUpdate()
        ).thenReturn(Optional.empty());

        // when
        int compensatedCount = service.compensateNextBatch();

        // then
        assertThat(compensatedCount).isZero();

        verifyNoInteractions(
                subscriptionRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("보상 대상 청약 배치를 COMPENSATING으로 전환하고 보상 Outbox를 저장한다")
    void compensatesSubscriptionBatch() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID secondSubscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription firstSubscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        Subscription secondSubscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.ADMIN_CANCELLED
                );

        when(firstSubscription.getSubscriptionId())
                .thenReturn(firstSubscriptionId);

        when(secondSubscription.getSubscriptionId())
                .thenReturn(secondSubscriptionId);

        when(
                offeringRepository
                        .findNextCancellationTargetForUpdate()
        ).thenReturn(Optional.of(offering));

        when(
                subscriptionRepository
                        .findCompensationBatchForUpdate(
                                offeringId,
                                100
                        )
        ).thenReturn(
                List.of(
                        firstSubscription,
                        secondSubscription
                )
        );

        // when
        int compensatedCount = service.compensateNextBatch();

        // then
        assertThat(compensatedCount).isEqualTo(2);

        verify(firstSubscription).startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        verify(secondSubscription).startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        verify(
                subscriptionCompensationRepository,
                org.mockito.Mockito.times(2)
        ).save(any(SubscriptionCompensation.class));

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        firstSubscription,
                        assetId,
                        offeringId.toString()
                );

        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        secondSubscription,
                        assetId,
                        offeringId.toString()
                );

        verify(
                subscriptionCompensationRepository
        ).flush();
    }

    @Test
    @DisplayName("모집 미달 공모의 청약 배치는 모집 미달 취소 유형으로 전환한다")
    void compensatesUnderSubscribedBatch() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(offering.getOfferingId())
                .thenReturn(offeringId);
        when(offering.getAssetId())
                .thenReturn(assetId);
        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.UNDER_SUBSCRIBED
                );
        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(offeringRepository.findNextCancellationTargetForUpdate())
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository.findCompensationBatchForUpdate(
                offeringId,
                100
        )).thenReturn(List.of(subscription));

        // when
        int compensatedCount = service.compensateNextBatch();

        // then
        assertThat(compensatedCount).isEqualTo(1);
        verify(subscription).startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );
        verify(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        offeringId.toString()
                );
        verify(subscriptionCompensationRepository).flush();
    }

    @Test
    @DisplayName("배치 처리 실패 시 공모 ID와 배치 청약 ID를 포함한 예외를 발생시킨다")
    void wrapsBatchFailureWithTargetIds() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.ADMIN_CANCELLED
                );

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(
                offeringRepository
                        .findNextCancellationTargetForUpdate()
        ).thenReturn(Optional.of(offering));

        when(
                subscriptionRepository
                        .findCompensationBatchForUpdate(
                                offeringId,
                                100
                        )
        ).thenReturn(List.of(subscription));

        org.mockito.Mockito.doThrow(
                        new IllegalStateException(
                                "Outbox 저장 실패"
                        )
                ).when(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        offeringId.toString()
                );

        // when & then
        assertThatThrownBy(service::compensateNextBatch)
                .isInstanceOfSatisfying(
                        OfferingCancellationBatchException.class,
                        exception -> {
                            assertThat(exception.getOfferingId())
                                    .isEqualTo(offeringId);

                            assertThat(
                                    exception.getSubscriptionIds()
                            ).containsExactly(subscriptionId);

                            assertThat(exception.getCause())
                                    .isInstanceOf(
                                            IllegalStateException.class
                                    );
                        }
                );

        verify(
                subscriptionCompensationRepository,
                never()
        ).flush();
    }

    @Test
    @DisplayName("flush 실패도 실패한 배치 ID를 포함한 예외로 변환한다")
    void wrapsFlushFailureWithTargetIds() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = org.mockito.Mockito.mock(
                Offering.class
        );

        Subscription subscription =
                org.mockito.Mockito.mock(
                        Subscription.class
                );

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(offering.getCancellationType())
                .thenReturn(
                        com.moneykk.moneytown.offering.offering.domain.entity
                                .CancellationType.ADMIN_CANCELLED
                );

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(
                offeringRepository
                        .findNextCancellationTargetForUpdate()
        ).thenReturn(Optional.of(offering));

        when(
                subscriptionRepository
                        .findCompensationBatchForUpdate(
                                offeringId,
                                100
                        )
        ).thenReturn(List.of(subscription));

        org.mockito.Mockito.doThrow(
                new IllegalStateException(
                        "flush 실패"
                )
        ).when(
                subscriptionCompensationRepository
        ).flush();

        // when & then
        assertThatThrownBy(service::compensateNextBatch)
                .isInstanceOfSatisfying(
                        OfferingCancellationBatchException.class,
                        exception -> {
                            assertThat(exception.getOfferingId())
                                    .isEqualTo(offeringId);

                            assertThat(
                                    exception.getSubscriptionIds()
                            ).containsExactly(subscriptionId);
                        }
                );
    }
}
