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
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingRevocationFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingRevocationSucceededPayload;
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
class HoldingRevocationResultServiceTest {

    private static final String GROUP = "offering-service";
    private static final String CORRELATION_ID = "test-correlation";
    private final UUID offeringId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();
    private Subscription subscription;
    private SubscriptionCompensation compensation;

    @Mock private ProcessedEventService processedEventService;
    @Mock private OfferingRepository offeringRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionCompensationRepository subscriptionCompensationRepository;
    @Mock private SubscriptionCompensationCompletionService subscriptionCompensationCompletionService;
    @InjectMocks private HoldingRevocationResultService service;

    @BeforeEach
    void setUp() {
        subscription = Subscription.create(
                offeringId, UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.startCompensation(CancellationType.OFFERING_UNDER_SUBSCRIBED);
        compensation = SubscriptionCompensation.create(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("실제 회수 성공을 저장하고 이전 오류를 지운 뒤 완료 판정을 호출한다")
    void appliesRevokedBeforeCompletion() {
        compensation.markHoldingFailed("HOLDING_REVOCATION_FAILED");
        stubContext();
        when(subscriptionCompensationCompletionService.completeIfReady(
                subscription.getSubscriptionId()
        )).thenAnswer(invocation -> {
            assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
            assertThat(compensation.getHoldingErrorCode()).isNull();
            return false;
        });

        assertThat(service.handleSucceeded(revoked(10L), GROUP)).isTrue();
        assertThat(compensation.getWalletStatus()).isEqualTo(CompensationStatus.PENDING);
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

    @ParameterizedTest
    @ValueSource(strings = {"NOT_ALLOCATED", "ALREADY_REVOKED"})
    @DisplayName("회수할 작업이 없는 NO_ACTION도 회수 성공으로 기록한다")
    void acceptsNoAction(String reason) {
        stubContext();
        var event = success(new HoldingRevocationSucceededPayload(
                assetId, null, 0L, "NO_ACTION", reason
        ));

        assertThat(service.handleSucceeded(event, GROUP)).isTrue();
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("회수 실패는 오류 코드를 저장하고 청약 보상을 계속 대기한다")
    void recordsFailureWithoutCompleting() {
        stubContext();

        assertThat(service.handleFailed(failure(), GROUP)).isTrue();
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.FAILED);
        assertThat(compensation.getHoldingErrorCode()).isEqualTo("HOLDING_REVOCATION_FAILED");
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.COMPENSATING);
        assertThat(subscription.isQuantityReserved()).isTrue();
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("회수 성공 후 늦은 실패가 도착해도 성공 상태를 보존한다")
    void preservesSuccessAgainstLateFailure() {
        compensation.markHoldingSucceeded();
        stubContext();

        assertThat(service.handleFailed(failure(), GROUP)).isTrue();
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(compensation.getHoldingErrorCode()).isNull();
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("다른 eventId의 성공 재전달이면 전체 완료 조건을 다시 확인한다")
    void rechecksCompletionForRepeatedSuccess() {
        compensation.markHoldingSucceeded();
        stubContext();

        assertThat(service.handleSucceeded(revoked(10L), GROUP)).isTrue();
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("수동 확인 중에도 Holding 결과를 저장하고 청약 상태는 유지한다")
    void recordsSuccessDuringManualReview() {
        subscription.requireManualReview("REVIEW_REQUIRED");
        stubContext();

        assertThat(service.handleSucceeded(revoked(10L), GROUP)).isTrue();
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.SUCCEEDED);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.MANUAL_REVIEW);
        verify(subscriptionCompensationCompletionService)
                .completeIfReady(subscription.getSubscriptionId());
    }

    @Test
    @DisplayName("청약 수량과 다른 회수 수량은 성공으로 반영하지 않는다")
    void rejectsWrongQuantity() {
        stubContext();

        assertThatThrownBy(() -> service.handleSucceeded(revoked(9L), GROUP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("회수 수량");
        assertThat(compensation.getHoldingStatus()).isEqualTo(CompensationStatus.PENDING);
        verifyNoInteractions(subscriptionCompensationCompletionService);
    }

    @Test
    @DisplayName("NO_ACTION의 수량이 0이 아니면 업무 처리 전에 거부한다")
    void rejectsWrongNoActionQuantity() {
        var event = success(new HoldingRevocationSucceededPayload(
                assetId, null, 1L, "NO_ACTION", "NOT_ALLOCATED"
        ));

        assertThatThrownBy(() -> service.handleSucceeded(event, GROUP))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(
                processedEventService, offeringRepository, subscriptionRepository,
                subscriptionCompensationRepository, subscriptionCompensationCompletionService
        );
    }

    @Test
    @DisplayName("알 수 없는 NO_ACTION 사유는 업무 처리 전에 거부한다")
    void rejectsUnknownNoActionReason() {
        var event = success(new HoldingRevocationSucceededPayload(
                assetId, null, 0L, "NO_ACTION", "UNKNOWN"
        ));

        assertThatThrownBy(() -> service.handleSucceeded(event, GROUP))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventService);
    }

    @Test
    @DisplayName("같은 Consumer Group에서 이미 처리한 이벤트이면 업무 조회를 생략한다")
    void skipsDuplicateEvent() {
        var event = revoked(10L);
        when(processedEventService.processOnce(eq(event), eq(GROUP), any(Runnable.class)))
                .thenReturn(false);

        assertThat(service.handleSucceeded(event, GROUP)).isFalse();
        verifyNoInteractions(
                offeringRepository, subscriptionRepository,
                subscriptionCompensationRepository, subscriptionCompensationCompletionService
        );
    }

    @Test
    @DisplayName("완료 처리 실패를 상위 트랜잭션으로 전달한다")
    void propagatesCompletionFailure() {
        stubContext();
        var failure = new IllegalStateException("수량 복원 실패");
        when(subscriptionCompensationCompletionService.completeIfReady(
                subscription.getSubscriptionId()
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.handleSucceeded(revoked(10L), GROUP))
                .isSameAs(failure);
    }

    private EventEnvelope<HoldingRevocationSucceededPayload> revoked(long quantity) {
        return success(new HoldingRevocationSucceededPayload(
                assetId, holdingId, quantity, "REVOKED", null
        ));
    }

    private EventEnvelope<HoldingRevocationSucceededPayload> success(
            HoldingRevocationSucceededPayload payload
    ) {
        return EventEnvelope.of(
                "HoldingRevocationSucceeded", subscription.getSubscriptionId().toString(),
                subscription.getUserId(), CORRELATION_ID, payload
        );
    }

    private EventEnvelope<HoldingRevocationFailedPayload> failure() {
        return EventEnvelope.of(
                "HoldingRevocationFailed", subscription.getSubscriptionId().toString(),
                subscription.getUserId(), CORRELATION_ID,
                new HoldingRevocationFailedPayload(
                        assetId, "HOLDING_REVOCATION_FAILED", "회수 처리 실패", true
                )
        );
    }

    private void stubContext() {
        // 실제 트랜잭션 대신 업무 콜백만 실행한다. DB 롤백 검증은 별도이다.
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(any(), eq(GROUP), any(Runnable.class));

        Offering offering = Offering.create(
                assetId, UUID.randomUUID(), "테스트 공모",
                1_000L, 100L, 1L, 100L,
                Instant.now().minusSeconds(3_600), Instant.now().minusSeconds(60)
        );
        when(subscriptionRepository.findOfferingIdBySubscriptionId(subscription.getSubscriptionId()))
                .thenReturn(Optional.of(offeringId));
        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(subscriptionRepository.findByIdForUpdate(subscription.getSubscriptionId()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionCompensationRepository.findBySubscriptionIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(compensation));
    }
}

