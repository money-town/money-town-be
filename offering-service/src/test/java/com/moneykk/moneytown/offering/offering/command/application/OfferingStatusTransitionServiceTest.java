package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingStatusTransitionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private OfferingStatusTransitionService offeringStatusTransitionService;

    @Test
    @DisplayName("SCHEDULED 공모의 OPEN 전환 건수를 반환한다")
    void opensScheduledOfferings() {
        when(offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(3);

        int result =
                offeringStatusTransitionService.openScheduledOfferings();

        assertThat(result).isEqualTo(3);

        verify(offeringRepository)
                .openScheduledOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("SOLD_OUT 공모의 CLOSED 전환 건수를 반환한다")
    void closesSoldOutOfferings() {
        when(offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(2);

        int result =
                offeringStatusTransitionService.closeSoldOutOfferings();

        assertThat(result).isEqualTo(2);

        verify(offeringRepository)
                .closeSoldOutOfferings(
                        JpaAuditingConfig.SYSTEM_USER_ID
                );
    }

    @Test
    @DisplayName("모집 종료된 OPEN 공모와 보상 대상 청약을 보상 진행 상태로 전환한다")
    void startsUnderSubscribedCancellations() {
        // given
        UUID offeringId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        Subscription processingSubscription = mock(Subscription.class);
        Subscription confirmedSubscription = mock(Subscription.class);

        when(offering.getOfferingId())
                .thenReturn(offeringId);

        when(offeringRepository
                .findAllByOfferingStatusAndEndAtLessThanEqualAndIsDeletedFalse(
                        eq(OfferingStatus.OPEN),
                        any(),
                        any()
                ))
                .thenReturn(List.of(offering));

        when(subscriptionRepository
                .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                        eq(offeringId),
                        eq(List.of(
                                SubscriptionStatus.PROCESSING,
                                SubscriptionStatus.CONFIRMED
                        ))
                ))
                .thenReturn(List.of(
                        processingSubscription,
                        confirmedSubscription
                ));

        // when
        int result =
                offeringStatusTransitionService
                        .startUnderSubscribedCancellations();

        // then
        assertThat(result).isEqualTo(1);

        verify(offering)
                .startUnderSubscribedCancellation();

        verify(processingSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );

        verify(confirmedSubscription)
                .startCompensation(
                        CancellationType.OFFERING_UNDER_SUBSCRIBED
                );
    }
}