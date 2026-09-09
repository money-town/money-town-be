package com.moneykk.moneytown.analysis.fds.query.dto;

import com.moneykk.moneytown.analysis.fds.domain.DetectionType;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FdsDetectionLogSearchCondition(
        @Schema(description = "사용자 ID 필터 (선택)")
        UUID userId,
        @Schema(description = "탐지 유형 필터 (선택)")
        DetectionType detectionType,
        @Schema(description = "규칙 코드 필터 (선택)")
        RuleCode ruleCode
) {
}
