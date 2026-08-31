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

    @Column(name = "carried_in_amount", nullable = false)
    private Long carriedInAmount;

    @Column(name = "remainder_amount", nullable = false)
    private Long remainderAmount;

    private SettlementBatch(UUID assetId, UUID revenueId, LocalDate recordDate,
                             Long distributableAmount, Long carriedInAmount) {
        this.id = UUID.randomUUID();
        this.assetId = assetId;
        this.revenueId = revenueId;
        this.recordDate = recordDate;
        this.carriedInAmount = carriedInAmount;
        this.totalAmount = distributableAmount + carriedInAmount;
        this.remainderAmount = 0L;
        this.status = SettlementStatus.PENDING;
    }

    public static SettlementBatch open(UUID assetId, UUID revenueId, LocalDate recordDate,
                                        Long distributableAmount, Long carriedInAmount) {
        return new SettlementBatch(assetId, revenueId, recordDate, distributableAmount, carriedInAmount);
    }

    public void markSnapshotTaken() {
        this.status = SettlementStatus.SNAPSHOT_TAKEN;
    }

    public void markCalculated(Long remainderAmount) {
        this.remainderAmount = remainderAmount;
        this.status = SettlementStatus.CALCULATED;
    }

    public void markDisbursing() {
        this.status = SettlementStatus.DISBURSING;
    }
}