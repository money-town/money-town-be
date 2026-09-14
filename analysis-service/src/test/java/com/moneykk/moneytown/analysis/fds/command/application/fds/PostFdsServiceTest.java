package com.moneykk.moneytown.analysis.fds.command.application.fds;

import com.moneykk.moneytown.analysis.fds.command.application.PostFdsDetectionApplier;
import com.moneykk.moneytown.analysis.fds.command.application.PostFdsService;
import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounter;
import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounts;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsDetectionLogRepository;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.FailureSource;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.SubscriptionEventPayload;
import com.moneykk.moneytown.analysis.global.config.PostFdsRuleProperties;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.common.event.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostFdsServiceTest {

    @Mock
    private PostFdsDetectionApplier postFdsDetectionApplier;
    @Mock
    private NotificationCommandService notificationCommandService;
    @Mock
    private PostFdsCounter postFdsCounter;
    @Mock
    private FdsUserStateRepository fdsUserStateRepository;
    @Mock
    private FdsDetectionLogRepository fdsDetectionLogRepository;

    private PostFdsService postFdsService;

    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PostFdsRuleProperties ruleProperties = new PostFdsRuleProperties(
                Map.of(
                        RuleCode.REPEATED_FAILURE, new PostFdsRuleProperties.PostThreshold(60, 8, null),
                        RuleCode.REPEATED_LIMIT_EXCEEDED, new PostFdsRuleProperties.PostThreshold(60, 5, null),
                        RuleCode.HIGH_CANCEL_RATE, new PostFdsRuleProperties.PostThreshold(86_400, 4, 10)
                ),
                Map.of(
                        FailureSource.WALLET_HOLD, Set.of("INSUFFICIENT_AVAILABLE_BALANCE", "BALANCE_OVERFLOW"),
                        FailureSource.SUBSCRIPTION, Set.of()
                )
        );

        postFdsService = new PostFdsService(
                postFdsDetectionApplier,
                notificationCommandService,
                ruleProperties,
                postFdsCounter,
                fdsUserStateRepository,
                fdsDetectionLogRepository
        );
    }

    private EventEnvelope<SubscriptionEventPayload> envelope(String eventType, FailureSource source, String reasonCode) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                subscriptionId.toString(),
                userId,
                Instant.now(),
                null,
                new SubscriptionEventPayload(userId, assetId, subscriptionId, source, reasonCode)
        );
    }

    private void stubNormalUser() {
        when(fdsDetectionLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("알 수 없는 eventType은 아무 처리 없이 skip한다")
    void handle_unknownEventType_skips() {
        var env = envelope("SomeOtherEvent", null, null);

        postFdsService.handle(env);

        verifyNoInteractions(fdsDetectionLogRepository, fdsUserStateRepository, postFdsCounter, postFdsDetectionApplier);
    }

    @Test
    @DisplayName("SubscriptionFailed인데 failureSource/failureReasonCode가 null이면 집계 없이 skip한다")
    void handle_subscriptionFailed_withNullFailureFields_skips() {
        var env = envelope("SubscriptionFailed", null, null);

        postFdsService.handle(env);

        verifyNoInteractions(fdsDetectionLogRepository, fdsUserStateRepository, postFdsCounter, postFdsDetectionApplier);
    }

    @Test
    @DisplayName("SubscriptionFailed인데 화이트리스트에 없는 실패 코드면(RESERVATION_EXPIRED 등) 집계에서 제외한다")
    void handle_subscriptionFailed_notWhitelisted_skipsAggregation() {
        var env = envelope("SubscriptionFailed", FailureSource.SUBSCRIPTION, "RESERVATION_EXPIRED");

        postFdsService.handle(env);

        verifyNoInteractions(fdsDetectionLogRepository, fdsUserStateRepository, postFdsCounter, postFdsDetectionApplier);
    }

    @Test
    @DisplayName("SubscriptionFailed이고 화이트리스트에 있는 실패 코드면 정상적으로 집계를 진행한다")
    void handle_subscriptionFailed_whitelisted_countsAggregation() {
        stubNormalUser();
        when(postFdsCounter.recordAndCount(any(), any(), any(), any()))
                .thenReturn(new PostFdsCounts(1, 0, 1, 0));
        var env = envelope("SubscriptionFailed", FailureSource.WALLET_HOLD, "INSUFFICIENT_AVAILABLE_BALANCE");

        postFdsService.handle(env);

        verify(postFdsCounter).recordAndCount(eq(userId), eq(eventId), any(), any());
        verifyNoInteractions(postFdsDetectionApplier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SubscriptionSuccess", "SubscriptionCancelled", "SubscriptionLimitExceeded", "SubscriptionRequest"})
    @DisplayName("실패 이벤트가 아닌 타입은 failureSource/failureReasonCode가 없어도 화이트리스트 체크 없이 집계된다")
    void handle_nonFailedEventTypes_areNotFilteredByAggregationWhitelist(String eventType) {
        stubNormalUser();
        when(postFdsCounter.recordAndCount(any(), any(), any(), any()))
                .thenReturn(new PostFdsCounts(0, 0, 1, 0));
        var env = envelope(eventType, null, null);

        postFdsService.handle(env);

        verify(postFdsCounter).recordAndCount(eq(userId), eq(eventId), any(), any());
    }

    @Test
    @DisplayName("이미 처리된 eventId(멱등)면 skip한다")
    void handle_alreadyProcessedEvent_skips() {
        when(fdsDetectionLogRepository.existsByEventId(eventId)).thenReturn(true);
        var env = envelope("SubscriptionFailed", FailureSource.WALLET_HOLD, "INSUFFICIENT_AVAILABLE_BALANCE");

        postFdsService.handle(env);

        verifyNoInteractions(fdsUserStateRepository, postFdsCounter, postFdsDetectionApplier);
    }

    @Test
    @DisplayName("이미 BLOCKED 상태인 유저 이벤트는 집계 없이 skip한다")
    void handle_userAlreadyBlocked_skips() {
        FdsUserState blocked = FdsUserState.create(userId);
        blocked.block("PRIOR_VIOLATION");

        when(fdsDetectionLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(blocked));
        var env = envelope("SubscriptionFailed", FailureSource.WALLET_HOLD, "INSUFFICIENT_AVAILABLE_BALANCE");

        postFdsService.handle(env);

        verifyNoInteractions(postFdsCounter, postFdsDetectionApplier);
    }

    @Test
    @DisplayName("임계치를 넘겨 BLOCKED로 전이되면 알림을 발송한다")
    void handle_violationCausesBlock_sendsNotification() {
        stubNormalUser();
        when(postFdsCounter.recordAndCount(any(), any(), any(), any()))
                .thenReturn(new PostFdsCounts(8, 0, 0, 0));
        when(postFdsDetectionApplier.apply(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(UserStatus.BLOCKED);
        var env = envelope("SubscriptionFailed", FailureSource.WALLET_HOLD, "INSUFFICIENT_AVAILABLE_BALANCE");

        postFdsService.handle(env);

        verify(notificationCommandService).send(eq(eventId), any(NotificationRequest.class));
        verify(postFdsCounter).clear(userId, RuleCode.REPEATED_FAILURE);
    }

    @Test
    @DisplayName("탐지 반영 중 동시 처리로 중복 제약 위반이 나면 조용히 무시한다")
    void handle_detectionApplyDuplicateConflict_skipsSilently() {
        stubNormalUser();
        when(postFdsCounter.recordAndCount(any(), any(), any(), any()))
                .thenReturn(new PostFdsCounts(8, 0, 0, 0));
        when(postFdsDetectionApplier.apply(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        var env = envelope("SubscriptionFailed", FailureSource.WALLET_HOLD, "INSUFFICIENT_AVAILABLE_BALANCE");

        postFdsService.handle(env);

        verifyNoInteractions(notificationCommandService);
    }
}
