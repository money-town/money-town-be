package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {

    Optional<SettlementBatch> findByRevenueIdAndIsDeletedFalse(UUID revenueId);

    boolean existsByAssetIdAndStatusNotInAndIsDeletedFalse(UUID assetId, Collection<SettlementStatus> terminalStatuses);

    // 자산당 진행 중 배치는 uk_settlement_batches_asset_in_progress로 최대 1건 — 실패 상태로 멈춰 자산을 막고 있는 배치를 찾는다
    Optional<SettlementBatch> findFirstByAssetIdAndStatusInAndIsDeletedFalse(UUID assetId, Collection<SettlementStatus> statuses);

    Optional<SettlementBatch> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);
}