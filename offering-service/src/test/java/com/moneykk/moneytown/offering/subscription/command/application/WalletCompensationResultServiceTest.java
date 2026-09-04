package com.moneykk.moneytown.offering.subscription.command.application;

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
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletCompensationResultPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletCompensationResultServiceTest {

    private static final String GROUP = "offering-service";
    private static final String CORRELATION_ID = "test-correlation";
    private final UUID offeringId = UUID.randomUUID();
    private Subscription subscription;
    private SubscriptionCompensation compensation;

    @Mock private ProcessedEventService processedEventService;
    @Mock private OfferingRepository offeringRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionCompensationRepository subscriptionCompensationRepository;
    @Mock private SubscriptionCompensationCompletionService subscriptionCompensationCompletionService;
    @InjectMocks private WalletCompensationResultService service;

    @BeforeEach
    void setUp() {
        subscription = Subscription.create(
                offeringId, UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.startCompensation(CancellationType.OFFERING_UNDER_SUBSCRIBED);
        compensation = SubscriptionCompensation.create(subscription.getSubscriptionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RELEASE", "REFUND", "NONE"})
    @DisplayName("유효한 Wallet 성공을 저장한 뒤 완료 판정을 호출한다")
    void appliesSuccessBeforeCompletion(String type) {
        compensation.markWalletFailed("HOLD_NOT_FOUND");
        stubContext();
        when(subscriptionCompensationCompletionService.completeIfReady(
                subscription.getSubscriptionId()
        )).thenAnswer(invocation -> {
            assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
            assertThat(compensation.getWalletErrorCode()).isNull();
            return false;
        });

        assertThat(service.handleSucceeded(success(type), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.PENDING);
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());

        var order = inOrder(
                offeringRepository, subscriptionRepository,
                subscriptionCompensationRepository
        );
        order.verify(offeringRepository).findByIdForUpdate(offeringId);
        order.verify(subscriptionRepository).findByIdForUpdate(subscription.getSubscriptionId());
        order.verify(subscriptionCompensationRepository)
                .findBySubscriptionIdForUpdate(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("Wallet 실패는 오류 코드만 기록하고 완료 판정을 호출하지 않는다")
    void recordsFailureWithoutCompleting() {
        stubContext();

        assertThat(service.handleFailed(failure(), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.FAILED);
        assertThat(compensation.getWalletErrorCode()).isEqualTo("HOLD_NOT_FOUND");
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("성공 이후 다른 eventId의 늦은 실패가 와도 성공 상태를 보존한다")
    void preservesSuccessAgainstLateFailure() {
        compensation.markWalletSucceeded();
        stubContext();

        assertThat(service.handleFailed(failure(), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(compensation.getWalletErrorCode()).isNull();
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("성공 재수신 시 상태를 유지하고 완료 조건은 다시 확인한다")
    void rechecksCompletionForRepeatedSuccess() {
        compensation.markWalletSucceeded();
        stubContext();

        assertThat(service.handleSucceeded(success("NONE"), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("이미 취소된 청약의 성공 재수신도 기존 취소 시각을 보존한다")
    void acceptsSuccessAfterCancellation() {
        compensation.markWalletSucceeded();
        compensation.markHoldingSucceeded();
        subscription.markCompensationQuantityRestored();
        Instant cancelledAt = Instant.now().minusSeconds(10);
        subscription.completeCancellation(cancelledAt);
        stubContext();

        assertThat(service.handleSucceeded(success("NONE"), GROUP)).isTrue();
        assertThat(subscription.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(subscription.isQuantityReserved()).isFalse();
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("수동 확인 중에도 Wallet 결과를 저장하고 청약 상태는 유지한다")
    void recordsSuccessDuringManualReview() {
        subscription.requireManualReview("REVIEW_REQUIRED");
        stubContext();

        assertThat(service.handleSucceeded(success("RELEASE"), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.MANUAL_REVIEW);
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("청약 금액과 다른 해제 금액은 성공으로 반영하지 않는다")
    void rejectsWrongAmount() {
        stubContext();
        var event = envelope("WalletCompensationSucceeded",
                new WalletCompensationResultPayload(10L, 20L, "RELEASE", 30L, 9_000L, null));

        assertThatThrownBy(() -> service.handleSucceeded(event, GROUP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액");
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.PENDING);
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("NONE 결과에 금액이 있으면 업무 처리 전에 거부한다")
    void rejectsMalformedNone() {
        var event = envelope("WalletCompensationSucceeded",
                new WalletCompensationResultPayload(10L, 20L, "NONE", null, 0L, null));

        assertThatThrownBy(() -> service.handleSucceeded(event, GROUP))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(
                processedEventService, offeringRepository, subscriptionRepository,
                subscriptionCompensationRepository, subscriptionCompensationCompletionService
        );
    }

    @Test
    @DisplayName("중복 처리 서비스가 이미 처리된 이벤트로 판단하면 업무 조회를 하지 않는다")
    void skipsDuplicateEvent() {
        var event = success("RELEASE");
        when(processedEventService.processOnce(eq(event), eq(GROUP), any(Runnable.class)))
                .thenReturn(false);

        assertThat(service.handleSucceeded(event, GROUP)).isFalse();
        verifyNoInteractions(
                offeringRepository, subscriptionRepository,
                subscriptionCompensationRepository, subscriptionCompensationCompletionService
        );
    }

    @Test
    @DisplayName("완료 서비스의 오류를 상위 트랜잭션으로 전달한다")
    void propagatesCompletionFailure() {
        stubContext();
        var failure = new IllegalStateException("수량 복원 실패");
        when(subscriptionCompensationCompletionService.completeIfReady(
                subscription.getSubscriptionId()
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.handleSucceeded(success("REFUND"), GROUP))
                .isSameAs(failure);
    }

    private EventEnvelope<WalletCompensationResultPayload> success(String type) {
        boolean none = "NONE".equals(type);
        return envelope("WalletCompensationSucceeded",
                new WalletCompensationResultPayload(
                        10L, 20L, type, none ? null : 30L,
                        none ? null : subscription.getAmount(), null
                ));
    }

    private EventEnvelope<WalletCompensationResultPayload> failure() {
        return envelope("WalletCompensationFailed",
                new WalletCompensationResultPayload(
                        null, null, null, null, null, "HOLD_NOT_FOUND"
                ));
    }

    private EventEnvelope<WalletCompensationResultPayload> envelope(
            String type, WalletCompensationResultPayload payload
    ) {
        return EventEnvelope.of(
                type, subscription.getSubscriptionId().toString(),
                subscription.getUserId(), CORRELATION_ID, payload
        );
    }

    private void stubContext() {
        // 실제 트랜잭션 대신 업무 콜백만 실행한다. DB 롤백 검증은 별도이다.
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(any(), eq(GROUP), any(Runnable.class));

        when(subscriptionRepository.findOfferingIdBySubscriptionId(subscription.getSubscriptionId()))
                .thenReturn(Optional.of(offeringId));
        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(mock(Offering.class)));
        when(subscriptionRepository.findByIdForUpdate(subscription.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionCompensationRepository.findBySubscriptionIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(compensation));
    }
}

