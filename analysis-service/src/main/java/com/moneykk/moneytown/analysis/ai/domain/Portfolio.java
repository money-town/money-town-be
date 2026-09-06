package com.moneykk.moneytown.analysis.ai.domain;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Table(name = "p_ai_portfolios")
@Entity
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ai_portfolio_id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "investment_amount", nullable = false)
    private Long investmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false, length = 20)
    private RiskType riskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "prefered_asset_type", length = 30)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AiStatus status;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "processing_time_ms")
    private Long processingTime;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private Portfolio(UUID idempotencyKey, UUID userId, Long investmentAmount,
                      RiskType riskType, AssetType assetType, String model, String promptVersion) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.investmentAmount = investmentAmount;
        this.riskType = riskType;
        this.assetType = assetType;
        this.status = AiStatus.PROCESSING;   // 초기 상태
        this.model = model;
        this.promptVersion = promptVersion;
    }

    public void complete(String response, long processingTime){
        this.response = response;
        this.processingTime = processingTime;
        this.status = AiStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage, long processingTime){
        this.errorMessage = errorMessage;
        this.status = AiStatus.FAILED;
        this.processingTime = processingTime;
        this.completedAt = Instant.now();
    }



}
