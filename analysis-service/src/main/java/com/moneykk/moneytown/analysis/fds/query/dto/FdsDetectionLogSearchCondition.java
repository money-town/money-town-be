package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;

import java.util.UUID;

public record FdsDetectionLogSearchCondition(
        UUID userId,
        DetectionType detectionType,
        RuleCode ruleCode
) {
}
