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

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_final_settlement_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalSettlementBatch extends BaseUpdatableEntity {

    @Id
    @Column(name = "final_settlement_batch_id")
    private UUID id;

    @Column(name = "asset_id", nullable = false, unique = true)
    private UUID assetId;

    @Column(name = "terminated_at", nullable = false)
    private Instant terminatedAt;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    private FinalSettlementBatch(UUID assetId, Instant terminatedAt, Long unitPrice, Long totalAmount) {
        this.id = UUID.randomUUID();
        this.assetId = assetId;
        this.terminatedAt = terminatedAt;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = SettlementStatus.PENDING;
    }

    public static FinalSettlementBatch open(UUID assetId, Instant terminatedAt, Long unitPrice, Long totalAmount) {
        return new FinalSettlementBatch(assetId, terminatedAt, unitPrice, totalAmount);
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