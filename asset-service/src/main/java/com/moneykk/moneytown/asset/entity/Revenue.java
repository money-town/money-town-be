package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_revenues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Revenue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "revenue_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private RevenueSourceType sourceType;

    @Column(name = "source_reference_id", nullable = false, length = 100)
    private String sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "revenue_type", nullable = false, length = 30)
    private RevenueType revenueType;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "expense_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expenseAmount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private RevenueTransferStatus transferStatus;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public Revenue(UUID assetId, UUID userId, RevenueSourceType sourceType,
                   String sourceReferenceId, RevenueType revenueType,
                   BigDecimal grossAmount, BigDecimal expenseAmount, BigDecimal feeAmount,
                   String currency, LocalDate periodStart, LocalDate periodEnd,
                   Map<String, Object> rawPayload) {
        if (grossAmount.signum() <= 0 || expenseAmount.signum() < 0 || feeAmount.signum() < 0) {
            throw new IllegalArgumentException("수익과 비용 금액이 올바르지 않습니다.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("수익 기간 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (!"KRW".equals(currency)) {
            throw new IllegalArgumentException("MVP에서는 KRW만 지원합니다.");
        }
        this.assetId = assetId;
        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceReferenceId = sourceReferenceId;
        this.revenueType = revenueType;
        this.grossAmount = grossAmount;
        this.expenseAmount = expenseAmount;
        this.feeAmount = feeAmount;
        this.currency = currency;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.rawPayload = new HashMap<>(rawPayload);
        this.transferStatus = RevenueTransferStatus.READY;
    }

    public void markTransferred() {
        this.transferStatus = RevenueTransferStatus.TRANSFERRED;
        this.transferredAt = Instant.now();
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("전달 실패 사유가 필요합니다.");
        }
        this.transferStatus = RevenueTransferStatus.FAILED;
        this.transferredAt = null;
        this.failureReason = failureReason;
    }

    public void retry() {
        this.transferStatus = RevenueTransferStatus.READY;
        this.transferredAt = null;
        this.failureReason = null;
    }
}
