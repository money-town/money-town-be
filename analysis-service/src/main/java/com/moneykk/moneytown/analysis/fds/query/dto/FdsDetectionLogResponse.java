package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;

import java.time.Instant;
import java.util.UUID;

public record FdsDetectionLogResponse(
        UUID fdsDetectionLogId,
        UUID requestId,
        UUID eventId,
        UUID userId,
        UUID assetId,
        DetectionType detectionType,
        EventType eventType,
        RuleCode ruleCode,
        Integer observedValue,
        Integer thresholdValue,
        Instant occurredAt
) {

    public static FdsDetectionLogResponse from(FdsDetectionLog l){
        return new FdsDetectionLogResponse(l.getId(), l.getRequestId(), l.getEventId(), l.getUserId(), l.getAssetId(),l.getDetectionType()
                ,l.getEventType(),l.getRuleCode(),l.getObservedValue(),l.getThresholdValue(),l.getOccurredAt());
    }
}
