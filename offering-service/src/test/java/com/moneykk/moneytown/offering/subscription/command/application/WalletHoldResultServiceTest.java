package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletHoldFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletHoldSucceededPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletHoldResultServiceTest {

    private static final String CONSUMER_GROUP = "offering-service";
    private static final String CORRELATION_ID = "test-correlation-id";

    private final UUID offeringId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Mock
    private SubscriptionCompensationRepository subscriptionCompensationRepository;

    @InjectMocks
    private WalletHoldResultService walletHoldResultService;

    @Test
    @DisplayName("PROCESSING 청약을 확정하고 다른 eventId의 성공 재수신은 중복 발행하지 않는다")
    void confirmsSubscriptionOnlyOnce() {
        Subscription subscription = newSubscription();
        executeBusinessAction();
        stubSubscription(subscription);
        stubOfferingForPublish();

        boolean firstResult = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        Instant confirmedAt = subscription.getConfirmedAt();

        boolean secondResult = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);
        assertThat(confirmedAt).isNotNull();
        assertThat(subscription.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(subscription.isQuantityReserved()).isTrue();

        verify(subscriptionEventPublisher, times(1)).publishConfirmed(
                subscription,
                assetId,
                CORRELATION_ID
        );
        verifyNoMoreInteractions(subscriptionEventPublisher);
        verifyNoInteractions(subscriptionCompensationRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = CompensationStatus.class,
            names = {"PENDING", "FAILED"}
    )
    @DisplayName("공모 취소 보상 중 늦은 동결 성공이 오면 보상을 재요청한다")
    void requestsCompensationForLateSuccess(
            CompensationStatus walletStatus
    ) {
        Subscription subscription = newSubscription();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscription.getSubscriptionId()
                );

        if (walletStatus == CompensationStatus.FAILED) {
            compensation.markWalletFailed("HOLD_NOT_FOUND");
        }

        executeBusinessAction();
        stubSubscription(subscription);
        stubCompensation(subscription, compensation);
        stubOfferingForPublish();

        boolean result = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(result).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.getConfirmedAt()).isNull();
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(walletStatus);
        assertThat(compensation.getHoldingStatus())
                .isEqualTo(CompensationStatus.PENDING);

        verify(subscriptionEventPublisher).publishCompensationRequested(
                subscription,
                assetId,
                CORRELATION_ID
        );
        verifyNoMoreInteractions(subscriptionEventPublisher);
    }

    @Test
    @DisplayName("Wallet 보상 완료 후 늦은 동결 성공은 보상을 재요청하지 않는다")
    void ignoresLateSuccessAfterWalletCompensation() {
        Subscription subscription = newSubscription();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscription.getSubscriptionId()
                );
        compensation.markWalletSucceeded();

        executeBusinessAction();
        stubSubscription(subscription);
        stubCompensation(subscription, compensation);

        boolean result = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(result).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.SUCCEEDED);

        verifyNoInteractions(
                subscriptionEventPublisher,
                offeringRepository
        );
    }

    @Test
    @DisplayName("타임아웃 후 늦은 동결 성공은 기존 실패 사유를 보존하며 수동 확인으로 전환한다")
    void marksTimedOutSubscriptionForManualReview() {
        Subscription subscription = newSubscription();
        subscription.startExpirationCompensation(
                subscription.getReservationExpiresAt()
        );

        executeBusinessAction();
        stubSubscription(subscription);
        stubCompensation(subscription, null);

        boolean result = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(result).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.MANUAL_REVIEW);
        assertThat(subscription.getFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");
        assertThat(subscription.getConfirmedAt()).isNull();
        assertThat(subscription.isQuantityReserved()).isTrue();

        verifyNoInteractions(
                subscriptionEventPublisher,
                offeringRepository
        );
    }

    @Test
    @DisplayName("공모 취소 보상 정보가 없으면 늦은 동결 성공을 수동 확인 대상으로 남긴다")
    void marksMissingCompensationForManualReview() {
        Subscription subscription = newSubscription();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        executeBusinessAction();
        stubSubscription(subscription);
        stubCompensation(subscription, null);

        boolean result = walletHoldResultService.handleSucceeded(
                succeededEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(result).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.MANUAL_REVIEW);
        assertThat(subscription.getFailureCode())
                .isEqualTo("LATE_WALLET_HOLD_SUCCEEDED");
        assertThat(subscription.getCancellationType())
                .isEqualTo(CancellationType.OFFERING_UNDER_SUBSCRIBED);
        assertThat(subscription.isQuantityReserved()).isTrue();

        verifyNoInteractions(
                subscriptionEventPublisher,
                offeringRepository
        );
    }

    @Test
    @DisplayName("보상 중 늦은 동결 실패는 청약 상태와 확보 수량을 유지한다")
    void preservesCompensatingSubscriptionOnLateFailure() {
        Subscription subscription = newSubscription();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        executeBusinessAction();
        stubSubscription(subscription);
        stubOfferingForFailure(subscription);

        boolean result = walletHoldResultService.handleFailed(
                failedEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(result).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        assertThat(subscription.getFailureCode()).isNull();

        verify(offeringRepository, never()).restoreQuantity(
                any(),
                any(),
                any()
        );
        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionCompensationRepository
        );
    }

    @Test
    @DisplayName("동결 실패는 수량 복원 후 거절하며 다른 eventId의 재수신에도 한 번만 복원한다")
    void rejectsSubscriptionAndRestoresQuantityOnlyOnce() {
        Subscription subscription = newSubscription();

        executeBusinessAction();
        stubSubscription(subscription);
        stubOfferingForFailure(subscription);

        when(offeringRepository.restoreQuantity(
                offeringId,
                subscription.getQuantity(),
                JpaAuditingConfig.SYSTEM_USER_ID
        )).thenReturn(1);

        boolean firstResult = walletHoldResultService.handleFailed(
                failedEvent(subscription),
                CONSUMER_GROUP
        );

        boolean secondResult = walletHoldResultService.handleFailed(
                failedEvent(subscription),
                CONSUMER_GROUP
        );

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.REJECTED);
        assertThat(subscription.isQuantityReserved()).isFalse();
        assertThat(subscription.getFailureCode())
                .isEqualTo("INSUFFICIENT_BALANCE");

        verify(offeringRepository, times(1)).restoreQuantity(
                offeringId,
                subscription.getQuantity(),
                JpaAuditingConfig.SYSTEM_USER_ID
        );
        verifyNoInteractions(
                subscriptionEventPublisher,
                subscriptionCompensationRepository
        );
    }

    @Test
    @DisplayName("이미 처리된 이벤트이면 업무 처리를 실행하지 않는다")
    void skipsBusinessActionWhenEventAlreadyProcessed() {
        Subscription subscription = newSubscription();
        EventEnvelope<WalletHoldSucceededPayload> event =
                succeededEvent(subscription);

        when(processedEventService.processOnce(
                eq(event),
                eq(CONSUMER_GROUP),
                any(Runnable.class)
        )).thenReturn(false);

        boolean result = walletHoldResultService.handleSucceeded(
                event,
                CONSUMER_GROUP
        );

        assertThat(result).isFalse();
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        verifyNoInteractions(
                subscriptionRepository,
                offeringRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("이벤트의 사용자가 청약자와 다르면 상태를 변경하지 않는다")
    void rejectsMismatchedUser() {
        Subscription subscription = newSubscription();

        EventEnvelope<WalletHoldSucceededPayload> event =
                EventEnvelope.of(
                        "WalletHoldSucceeded",
                        subscription.getSubscriptionId().toString(),
                        UUID.randomUUID(),
                        CORRELATION_ID,
                        new WalletHoldSucceededPayload(
                                100L,
                                200L,
                                "HELD"
                        )
                );

        executeBusinessAction();
        stubSubscription(subscription);

        assertThatThrownBy(() ->
                walletHoldResultService.handleSucceeded(
                        event,
                        CONSUMER_GROUP
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        verifyNoInteractions(
                offeringRepository,
                subscriptionCompensationRepository,
                subscriptionEventPublisher
        );
    }

    @Test
    @DisplayName("보상 재요청 저장에 실패하면 예외를 삼키지 않는다")
    void propagatesCompensationPublishFailure() {
        Subscription subscription = newSubscription();
        subscription.startCompensation(
                CancellationType.OFFERING_UNDER_SUBSCRIBED
        );

        SubscriptionCompensation compensation =
                SubscriptionCompensation.create(
                        subscription.getSubscriptionId()
                );

        executeBusinessAction();
        stubSubscription(subscription);
        stubCompensation(subscription, compensation);
        stubOfferingForPublish();

        IllegalStateException failure =
                new IllegalStateException("Outbox 저장 실패");

        doThrow(failure)
                .when(subscriptionEventPublisher)
                .publishCompensationRequested(
                        subscription,
                        assetId,
                        CORRELATION_ID
                );

        assertThatThrownBy(() ->
                walletHoldResultService.handleSucceeded(
                        succeededEvent(subscription),
                        CONSUMER_GROUP
                )
        ).isSameAs(failure);

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(compensation.getWalletStatus())
                .isEqualTo(CompensationStatus.PENDING);
    }

    private Subscription newSubscription() {
        return Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );
    }

    private EventEnvelope<WalletHoldSucceededPayload> succeededEvent(
            Subscription subscription
    ) {
        return EventEnvelope.of(
                "WalletHoldSucceeded",
                subscription.getSubscriptionId().toString(),
                subscription.getUserId(),
                CORRELATION_ID,
                new WalletHoldSucceededPayload(
                        100L,
                        200L,
                        "HELD"
                )
        );
    }

    private EventEnvelope<WalletHoldFailedPayload> failedEvent(
            Subscription subscription
    ) {
        return EventEnvelope.of(
                "WalletHoldFailed",
                subscription.getSubscriptionId().toString(),
                subscription.getUserId(),
                CORRELATION_ID,
                new WalletHoldFailedPayload(
                        200L,
                        "FAILED",
                        "INSUFFICIENT_BALANCE"
                )
        );
    }

    // 단위 테스트에서는 트랜잭션 대신 업무 콜백만 실행한다.
    private void executeBusinessAction() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(
                any(),
                eq(CONSUMER_GROUP),
                any(Runnable.class)
        );
    }

    private void stubSubscription(Subscription subscription) {
        when(subscriptionRepository.findByIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(subscription));
    }

    private void stubCompensation(
            Subscription subscription,
            SubscriptionCompensation compensation
    ) {
        when(subscriptionCompensationRepository
                .findBySubscriptionIdForUpdate(
                        subscription.getSubscriptionId()
                )
        ).thenReturn(Optional.ofNullable(compensation));
    }

    private void stubOfferingForPublish() {
        Offering offering = mock(Offering.class);

        when(offering.getAssetId()).thenReturn(assetId);
        when(offeringRepository.findById(offeringId))
                .thenReturn(Optional.of(offering));
    }

    private void stubOfferingForFailure(Subscription subscription) {
        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(mock(Offering.class)));
    }
}