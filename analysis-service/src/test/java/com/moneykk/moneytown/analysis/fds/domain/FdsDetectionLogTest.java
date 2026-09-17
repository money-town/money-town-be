package com.moneykk.moneytown.analysis.fds.domain;

import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FdsDetectionLogTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @Test
    @DisplayName("PRE 타입인데 requestId가 없으면 생성할 수 없다")
    void build_preWithoutRequestId_throws() {
        assertThatThrownBy(() -> FdsDetectionLog.builder()
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.PRE)
                .eventType(EventType.SUBSCRIPTION_REQUEST)
                .ruleCode(RuleCode.RAPID_REQUEST)
                .observedValue(5)
                .thresholdValue(5)
                .occurredAt(Instant.now())
                .build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("POST 타입인데 eventId가 없으면 생성할 수 없다")
    void build_postWithoutEventId_throws() {
        assertThatThrownBy(() -> FdsDetectionLog.builder()
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.POST)
                .eventType(EventType.SUBSCRIPTION_FAILED)
                .ruleCode(RuleCode.REPEATED_FAILURE)
                .observedValue(8)
                .thresholdValue(8)
                .occurredAt(Instant.now())
                .build())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PRE 타입은 requestId가 있으면 정상 생성된다")
    void build_preWithRequestId_succeeds() {
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();

        FdsDetectionLog log = FdsDetectionLog.builder()
                .requestId(requestId)
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.PRE)
                .eventType(EventType.SUBSCRIPTION_REQUEST)
                .ruleCode(RuleCode.RAPID_REQUEST)
                .observedValue(5)
                .thresholdValue(5)
                .occurredAt(now)
                .build();

        assertThat(log.getRequestId()).isEqualTo(requestId);
        assertThat(log.getEventId()).isNull();
        assertThat(log.getDetectionType()).isEqualTo(DetectionType.PRE);
        assertThat(log.getOccurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("POST 타입은 eventId가 있으면 정상 생성된다")
    void build_postWithEventId_succeeds() {
        UUID eventId = UUID.randomUUID();

        FdsDetectionLog log = FdsDetectionLog.builder()
                .eventId(eventId)
                .userId(userId)
                .assetId(assetId)
                .detectionType(DetectionType.POST)
                .eventType(EventType.SUBSCRIPTION_FAILED)
                .ruleCode(RuleCode.REPEATED_FAILURE)
                .observedValue(8)
                .thresholdValue(8)
                .occurredAt(Instant.now())
                .build();

        assertThat(log.getEventId()).isEqualTo(eventId);
        assertThat(log.getRequestId()).isNull();
        assertThat(log.getDetectionType()).isEqualTo(DetectionType.POST);
    }
}
