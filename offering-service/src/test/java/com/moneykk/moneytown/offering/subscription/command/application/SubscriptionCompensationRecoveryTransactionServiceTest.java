package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationRecoveryTransactionServiceTest {

    private final UUID offeringId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    @Mock
    private SubscriptionLifecycleMetrics
            subscriptionLifecycleMetrics;

    @InjectMocks
    private SubscriptionCompensationRecoveryTransactionService service;

    private Subscription subscription;
    private SubscriptionCompensation compensation;

    @BeforeEach
    void setUp() {
        subscription = mock(Subscription.class);
        compensation = mock(SubscriptionCompensation.class);
    }

    @Test
    @DisplayName(
            "기준 시각 이전부터 완료되지 않은 보상은 "
                    + "수동 확인 대상으로 전환한다"
    )
    void marksStuckCompensationForManualReview() {
        Instant stuckBefore = Instant.now().minusSeconds(300);

        stubLockedContext();

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        when(compensation.isExternalCompensationCompleted())
                .thenReturn(false);

        when(compensation.getUpdatedAt())
                .thenReturn(stuckBefore.minusSeconds(1));

        when(compensation.getWalletStatus())
                .thenReturn(CompensationStatus.FAILED);

        when(compensation.getHoldingStatus())
                .thenReturn(CompensationStatus.PENDING);

        boolean result = service.markStuckForManualReview(
                subscriptionId,
                stuckBefore
        );

        assertThat(result).isTrue();

        verify(subscription).requireManualReview(
                "COMPENSATION_RESULT_TIMEOUT"
        );

        verify(subscriptionLifecycleMetrics)
                .publishOutcome(
                        eq(subscription),
                        eq(
                                SubscriptionLifecycleMetrics.Result
                                        .MANUAL_REVIEW
                        ),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName(
            "후보 조회 이후 보상 결과가 갱신됐으면 "
                    + "수동 확인 대상으로 변경하지 않는다"
    )
    void skipsRecentlyUpdatedCompensation() {
        Instant stuckBefore = Instant.now().minusSeconds(300);

        stubLockedContext();

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        when(compensation.isExternalCompensationCompleted())
                .thenReturn(false);

        when(compensation.getUpdatedAt())
                .thenReturn(stuckBefore.plusSeconds(1));

        boolean result = service.markStuckForManualReview(
                subscriptionId,
                stuckBefore
        );

        assertThat(result).isFalse();

        verify(subscription, never())
                .requireManualReview(any());

        verifyNoInteractions(subscriptionLifecycleMetrics);
    }

    @Test
    @DisplayName(
            "Wallet과 Holding 보상이 모두 완료됐으면 "
                    + "수동 확인 대상으로 변경하지 않는다"
    )
    void skipsCompletedExternalCompensation() {
        Instant stuckBefore = Instant.now().minusSeconds(300);

        stubLockedContext();

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        when(compensation.isExternalCompensationCompleted())
                .thenReturn(true);

        boolean result = service.markStuckForManualReview(
                subscriptionId,
                stuckBefore
        );

        assertThat(result).isFalse();

        verify(subscription, never())
                .requireManualReview(any());

        verifyNoInteractions(subscriptionLifecycleMetrics);
    }

    @Test
    @DisplayName(
            "후보 조회 이후 청약 상태가 변경됐으면 "
                    + "보상 진행 정보를 잠그지 않는다"
    )
    void skipsSubscriptionThatIsNoLongerCompensating() {
        Instant stuckBefore = Instant.now().minusSeconds(300);

        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscriptionId
        )).thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(mock(Offering.class)));

        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        boolean result = service.markStuckForManualReview(
                subscriptionId,
                stuckBefore
        );

        assertThat(result).isFalse();

        verifyNoInteractions(
                subscriptionCompensationRepository,
                subscriptionLifecycleMetrics
        );
    }

    private void stubLockedContext() {
        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscriptionId
        )).thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(mock(Offering.class)));

        when(subscriptionRepository.findByIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscriptionCompensationRepository
                .findBySubscriptionIdForUpdate(subscriptionId))
                .thenReturn(Optional.of(compensation));
    }
}