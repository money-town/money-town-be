package com.moneykk.moneytown.analysis.fds.domain;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Table(name = "p_fds_detection_logs",
    indexes = {
        @Index(name = "idx_fds_detection_user", columnList = "user_id, occurred_at DESC")
    }
)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FdsDetectionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fds_detection_log_id")
    private UUID id;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_type", length = 10, nullable = false)
    private DetectionType detectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", length = 50, nullable = false)
    private RuleCode ruleCode;

    @Column(name = "observed_value", nullable = false)
    private Integer observedValue;

    @Column(name = "threshold_value", nullable = false)
    private Integer thresholdValue;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Builder
    private FdsDetectionLog(UUID requestId, UUID eventId, UUID userId, UUID assetId, DetectionType detectionType, EventType eventType, RuleCode ruleCode,
            Integer observedValue,Integer thresholdValue, Instant occurredAt
    ){
        if(detectionType == DetectionType.PRE && requestId == null){
            throw new BusinessException(AnalysisErrorCode.FDS_INVALID_REQUEST);
        }
        if(detectionType == DetectionType.POST && eventId == null){
            throw new BusinessException(AnalysisErrorCode.FDS_INVALID_REQUEST);
        }

        this.requestId = requestId;
        this.eventId = eventId;
        this.userId = userId;
        this.assetId = assetId;
        this.detectionType = detectionType;
        this.eventType = eventType;
        this.ruleCode = ruleCode;
        this.observedValue = observedValue;
        this.thresholdValue = thresholdValue;
        this.occurredAt = occurredAt;
    }

}
