package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FdsDetectionLogResponse(
        @Schema(description = "탐지 로그 ID")
        UUID fdsDetectionLogId,
        @Schema(description = "연관 요청 ID")
        UUID requestId,
        @Schema(description = "연관 이벤트 ID")
        UUID eventId,
        @Schema(description = "대상 사용자 ID")
        UUID userId,
        @Schema(description = "대상 자산 ID")
        UUID assetId,
        @Schema(description = "탐지 유형 (PRE: 사전 즉시 차단 / POST: 사후 단계적 상향)")
        DetectionType detectionType,
        @Schema(description = "이벤트 유형")
        EventType eventType,
        @Schema(description = "적용된 규칙 코드")
        RuleCode ruleCode,
        @Schema(description = "관측값")
        Integer observedValue,
        @Schema(description = "임계값")
        Integer thresholdValue,
        @Schema(description = "탐지 시각")
        Instant occurredAt
) {

    public static FdsDetectionLogResponse from(FdsDetectionLog l){
        return new FdsDetectionLogResponse(l.getId(), l.getRequestId(), l.getEventId(), l.getUserId(), l.getAssetId(),l.getDetectionType()
                ,l.getEventType(),l.getRuleCode(),l.getObservedValue(),l.getThresholdValue(),l.getOccurredAt());
    }
}
