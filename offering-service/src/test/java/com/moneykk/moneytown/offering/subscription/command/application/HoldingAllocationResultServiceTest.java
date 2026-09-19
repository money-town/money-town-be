package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingAllocationFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingAllocationSucceededPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingAllocationResultServiceTest {

    private static final String CONSUMER_GROUP =
            "offering-service";

    private static final String CORRELATION_ID =
            "test-correlation-id";

    private final UUID offeringId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    private Subscription subscription;
    private Offering offering;

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OfferingRepository offeringRepository;

    @InjectMocks
    private HoldingAllocationResultService service;

    @BeforeEach
    void setUp() {
        subscription = createProcessingSubscription();
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        offering = mock(Offering.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ALLOCATED",
            "ALREADY_PROCESSED"
    })
    @DisplayName("Holding 배정 성공 결과를 청약 후처리 성공으로 기록한다")
    void recordsAllocationSuccess(String result) {
        // given
        stubProcessingAndContext();

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        result,
                        subscription.getQuantity(),
                        subscription.getUserId(),
                        assetId
                );

        // when
        boolean processed = service.handleSucceeded(
                event,
                CONSUMER_GROUP
        );

        // then
        assertThat(processed).isTrue();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();

        InOrder order = inOrder(
                offeringRepository,
                subscriptionRepository
        );

        order.verify(subscriptionRepository)
                .findOfferingIdBySubscriptionId(
                        subscription.getSubscriptionId()
                );

        order.verify(offeringRepository)
                .findByIdForUpdate(offeringId);

        order.verify(subscriptionRepository)
                .findByIdForUpdate(
                        subscription.getSubscriptionId()
                );
    }

    @Test
    @DisplayName("Holding 배정 실패 코드만 기록하고 청약은 CONFIRMED로 유지한다")
    void recordsAllocationFailure() {
        // given
        stubProcessingAndContext();

        // when
        boolean processed = service.handleFailed(
                failureEvent(
                        subscription.getUserId(),
                        assetId
                ),
                CONSUMER_GROUP
        );

        // then
        assertThat(processed).isTrue();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.FAILED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isEqualTo("HOLDING_ALLOCATION_FAILED");
    }

    @Test
    @DisplayName("Holding 배정 실패 후 성공하면 성공으로 변경하고 오류 코드를 지운다")
    void replacesFailureWithSuccess() {
        // given
        subscription.markHoldingAllocationFailed(
                "HOLDING_ALLOCATION_FAILED"
        );

        stubProcessingAndContext();

        // when
        service.handleSucceeded(
                successEvent(
                        "ALLOCATED",
                        subscription.getQuantity(),
                        subscription.getUserId(),
                        assetId
                ),
                CONSUMER_GROUP
        );

        // then
        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();
    }

    @Test
    @DisplayName("Holding 배정 성공 후 늦은 실패가 도착해도 성공 상태를 유지한다")
    void preservesSuccessAgainstLateFailure() {
        // given
        subscription.markHoldingAllocationSucceeded();

        stubProcessingAndContext();

        // when
        service.handleFailed(
                failureEvent(
                        subscription.getUserId(),
                        assetId
                ),
                CONSUMER_GROUP
        );

        // then
        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();
    }

    @Test
    @DisplayName("같은 eventId를 이미 처리했다면 업무 조회를 실행하지 않는다")
    void skipsDuplicateEvent() {
        // given
        when(processedEventService.processOnce(
                any(),
                eq(CONSUMER_GROUP),
                any(Runnable.class)
        )).thenReturn(false);

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED",
                        subscription.getQuantity(),
                        subscription.getUserId(),
                        assetId
                );

        // when
        boolean processed = service.handleSucceeded(
                event,
                CONSUMER_GROUP
        );

        // then
        assertThat(processed).isFalse();

        verifyNoInteractions(
                subscriptionRepository,
                offeringRepository
        );
    }

    @Test
    @DisplayName("Holding 배정 수량이 청약 수량과 다르면 성공으로 기록하지 않는다")
    void rejectsWrongQuantity() {
        // given
        stubProcessingAndContext();

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED",
                        subscription.getQuantity() - 1,
                        subscription.getUserId(),
                        assetId
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleSucceeded(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("배정 수량");

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);
    }

    @Test
    @DisplayName("Holding 배정 결과의 사용자가 청약자와 다르면 거부한다")
    void rejectsWrongUser() {
        // given
        stubProcessingAndContext();

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED",
                        subscription.getQuantity(),
                        UUID.randomUUID(),
                        assetId
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleSucceeded(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);
    }

    @Test
    @DisplayName("Holding 배정 결과의 자산이 공모 자산과 다르면 거부한다")
    void rejectsWrongAsset() {
        // given
        stubProcessingAndContext();

        EventEnvelope<HoldingAllocationFailedPayload> event =
                failureEvent(
                        subscription.getUserId(),
                        UUID.randomUUID()
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleFailed(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assetId");

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);
    }

    @Test
    @DisplayName("지원하지 않는 성공 result는 업무 처리 전에 거부한다")
    void rejectsUnsupportedResult() {
        // given
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "UNKNOWN",
                        subscription.getQuantity(),
                        subscription.getUserId(),
                        assetId
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleSucceeded(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result");

        verifyNoInteractions(
                processedEventService,
                subscriptionRepository,
                offeringRepository
        );
    }

    @Test
    @DisplayName("지분 배정을 시작하지 않은 청약의 결과는 반영하지 않는다")
    void rejectsResultBeforeAllocationStarted() {
        // given
        subscription = createProcessingSubscription();

        stubProcessingAndContext();

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED",
                        subscription.getQuantity(),
                        subscription.getUserId(),
                        assetId
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleSucceeded(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지분 배정을 요청하지 않은");

        assertThat(subscription.getHoldingAllocationStatus())
                .isNull();
    }

    @Test
    @DisplayName("실패 이벤트에 retryable이 없으면 업무 처리 전에 거부한다")
    void rejectsFailureWithoutRetryable() {
        // given
        EventEnvelope<HoldingAllocationFailedPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationFailed",
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        CORRELATION_ID,
                        new HoldingAllocationFailedPayload(
                                assetId,
                                "HOLDING_ALLOCATION_FAILED",
                                "지분 배정 처리 실패",
                                null
                        )
                );

        // when & then
        assertThatThrownBy(() ->
                service.handleFailed(
                        event,
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("retryable");

        verifyNoInteractions(
                processedEventService,
                subscriptionRepository,
                offeringRepository
        );
    }

    @Test
    @DisplayName("HoldingAllocationSucceeded가 아닌 이벤트 타입은 거부한다")
    void rejectsWrongSucceededEventType() {
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                EventEnvelope.of(
                        "WrongType",
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        CORRELATION_ID,
                        new HoldingAllocationSucceededPayload(
                                assetId, holdingId, 10L, "ALLOCATED"
                        )
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("HoldingAllocationSucceeded");

        verifyNoInteractions(processedEventService);
    }

    @Test
    @DisplayName("correlationId가 없으면 성공 이벤트를 거부한다")
    void rejectsSucceededEventWithoutCorrelationId() {
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationSucceeded",
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        " ",
                        new HoldingAllocationSucceededPayload(
                                assetId, holdingId, 10L, "ALLOCATED"
                        )
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("correlationId");
    }

    @Test
    @DisplayName("aggregateId가 UUID 형식이 아니면 성공 이벤트를 거부한다")
    void rejectsSucceededEventWithNonUuidAggregateId() {
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationSucceeded",
                        "not-a-uuid",
                        subscription.getUserId(),
                        CORRELATION_ID,
                        new HoldingAllocationSucceededPayload(
                                assetId, holdingId, 10L, "ALLOCATED"
                        )
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("UUID");
    }

    @Test
    @DisplayName("quantity가 양수가 아니면 성공 이벤트를 거부한다")
    void rejectsSucceededEventWithInvalidQuantity() {
        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED", 0L,
                        subscription.getUserId(), assetId
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("quantity");

        verifyNoInteractions(processedEventService);
    }

    @Test
    @DisplayName("errorCode가 없거나 너무 길면 실패 이벤트를 거부한다")
    void rejectsFailedEventWithInvalidErrorCode() {
        EventEnvelope<HoldingAllocationFailedPayload> event =
                EventEnvelope.of(
                        "HoldingAllocationFailed",
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        CORRELATION_ID,
                        new HoldingAllocationFailedPayload(
                                assetId, "a".repeat(101), "메시지", true
                        )
                );

        assertThatThrownBy(() ->
                service.handleFailed(event, CONSUMER_GROUP)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("errorCode");
    }

    @Test
    @DisplayName("청약의 공모 ID를 찾을 수 없으면 성공 처리를 거부한다")
    void rejectsSucceededEventWhenOfferingIdLookupMissing() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(
                any(), eq(CONSUMER_GROUP), any(Runnable.class)
        );

        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.empty());

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED", 10L,
                        subscription.getUserId(), assetId
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(BusinessException.class)
         .satisfies(exception -> assertThat(
                 ((BusinessException) exception).getErrorCode()
         ).isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    @Test
    @DisplayName("공모를 찾을 수 없으면 성공 처리를 거부한다")
    void rejectsSucceededEventWhenOfferingMissing() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(
                any(), eq(CONSUMER_GROUP), any(Runnable.class)
        );

        when(subscriptionRepository.findOfferingIdBySubscriptionId(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED", 10L,
                        subscription.getUserId(), assetId
                );

        assertThatThrownBy(() ->
                service.handleSucceeded(event, CONSUMER_GROUP)
        ).isInstanceOf(BusinessException.class)
         .satisfies(exception -> assertThat(
                 ((BusinessException) exception).getErrorCode()
         ).isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND));
    }

    @Test
    @DisplayName("확정 이후 상태가 바뀐 청약에 늦은 배정 성공이 오면 상태를 변경하지 않는다")
    void ignoresLateSuccessAfterStatusChanged() {
        stubProcessingAndContext();

        subscription.startCompensation(
                com.moneykk.moneytown.offering.subscription.domain.entity
                        .CancellationType.OFFERING_ADMIN_CANCELLED
        );

        EventEnvelope<HoldingAllocationSucceededPayload> event =
                successEvent(
                        "ALLOCATED", 10L,
                        subscription.getUserId(), assetId
                );

        boolean result = service.handleSucceeded(event, CONSUMER_GROUP);

        assertThat(result).isTrue();
        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);
        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);
    }

    /**
     * 실제 ProcessedEventService 대신 업무 Callback을 실행한다.
     *
     * DB 처리 이력과 업무 변경의 실제 롤백은
     * 별도 통합 테스트에서 검증한다.
     */
    private void stubProcessingAndContext() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        }).when(processedEventService).processOnce(
                any(),
                eq(CONSUMER_GROUP),
                any(Runnable.class)
        );

        when(subscriptionRepository
                .findOfferingIdBySubscriptionId(
                        subscription.getSubscriptionId()
                ))
                .thenReturn(Optional.of(offeringId));

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        /*
         * 잘못된 userId 검증에서는 assetId 조회 전에 예외가 발생할 수 있으므로
         * Mockito의 불필요한 Stub 판정을 피하도록 lenient를 사용한다.
         */
        lenient()
                .when(offering.getAssetId())
                .thenReturn(assetId);

        when(subscriptionRepository.findByIdForUpdate(
                subscription.getSubscriptionId()
        )).thenReturn(Optional.of(subscription));
    }

    private Subscription createProcessingSubscription() {
        return Subscription.create(
                offeringId,
                UUID.randomUUID(),
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );
    }

    private EventEnvelope<HoldingAllocationSucceededPayload>
    successEvent(
            String result,
            Long quantity,
            UUID userId,
            UUID eventAssetId
    ) {
        return EventEnvelope.of(
                "HoldingAllocationSucceeded",
                subscription.getSubscriptionId().toString(),
                userId,
                CORRELATION_ID,
                new HoldingAllocationSucceededPayload(
                        eventAssetId,
                        holdingId,
                        quantity,
                        result
                )
        );
    }

    private EventEnvelope<HoldingAllocationFailedPayload>
    failureEvent(
            UUID userId,
            UUID eventAssetId
    ) {
        return EventEnvelope.of(
                "HoldingAllocationFailed",
                subscription.getSubscriptionId().toString(),
                userId,
                CORRELATION_ID,
                new HoldingAllocationFailedPayload(
                        eventAssetId,
                        "HOLDING_ALLOCATION_FAILED",
                        "지분 배정 처리 실패",
                        true
                )
        );
    }
}