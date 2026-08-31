package com.moneykk.moneytown.settlement.domain.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_holdings_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldingSnapshot extends BaseEntity {

    @Id
    @Column(name = "holding_snapshot_id")
    private UUID id;

    @Column(name = "settlement_batch_id", nullable = false, unique = true)
    private UUID settlementBatchId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDate snapshotAt;

    @Column(name = "total_quantity", nullable = false)
    private Long totalQuantity;

    @Column(name = "total_holders", nullable = false)
    private Integer totalHolders;

    @Column(name = "total_share_quantity")
    private Long totalShareQuantity;

    private HoldingSnapshot(UUID settlementBatchId, UUID assetId, LocalDate snapshotAt,
                             Long totalQuantity, Integer totalHolders, Long totalShareQuantity) {
        this.id = UUID.randomUUID();
        this.settlementBatchId = settlementBatchId;
        this.assetId = assetId;
        this.snapshotAt = snapshotAt;
        this.totalQuantity = totalQuantity;
        this.totalHolders = totalHolders;
        this.totalShareQuantity = totalShareQuantity;
    }

    public static HoldingSnapshot capture(UUID settlementBatchId, UUID assetId, LocalDate snapshotAt,
                                           Long totalQuantity, Integer totalHolders, Long totalShareQuantity) {
        return new HoldingSnapshot(settlementBatchId, assetId, snapshotAt, totalQuantity, totalHolders, totalShareQuantity);
    }
}