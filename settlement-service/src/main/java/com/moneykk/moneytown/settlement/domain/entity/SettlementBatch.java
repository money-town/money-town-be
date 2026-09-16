package com.moneykk.moneytown.settlement.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_settlement_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementBatch extends BaseUpdatableEntity {

    @Id
    @Column(name = "settlement_batch_id")
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "revenue_id", nullable = false, unique = true)
    private UUID revenueId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    private SettlementBatch(UUID assetId, UUID revenueId, LocalDate recordDate, Long totalAmount) {
        this.id = UUID.randomUUID();
        this.assetId = assetId;
        this.revenueId = revenueId;
        this.recordDate = recordDate;
        this.totalAmount = totalAmount;
        this.status = SettlementStatus.PENDING;
    }

    public static SettlementBatch open(UUID assetId, UUID revenueId, LocalDate recordDate, Long totalAmount) {
        return new SettlementBatch(assetId, revenueId, recordDate, totalAmount);
    }

    public void markSnapshotTaken() {
        this.status = SettlementStatus.SNAPSHOT_TAKEN;
    }

    public void markCalculated() {
        this.status = SettlementStatus.CALCULATED;
    }

    public void markDisbursing() {
        this.status = SettlementStatus.DISBURSING;
    }

    public void markCompleted() {
        this.status = SettlementStatus.COMPLETED;
    }

    public void markPartialFailed() {
        this.status = SettlementStatus.PARTIAL_FAILED;
    }

    public void markFailed() {
        this.status = SettlementStatus.FAILED;
    }
}