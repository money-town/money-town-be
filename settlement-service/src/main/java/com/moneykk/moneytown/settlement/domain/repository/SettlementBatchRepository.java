package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {

    boolean existsByRevenueIdAndIsDeletedFalse(UUID revenueId);

    boolean existsByAssetIdAndStatusNotAndIsDeletedFalse(UUID assetId, SettlementStatus status);

    Optional<SettlementBatch> findFirstByAssetIdAndStatusAndCarriedOutToBatchIdIsNullAndRemainderAmountGreaterThanAndIsDeletedFalseOrderByRecordDateDescCreatedAtDesc(
            UUID assetId, SettlementStatus status, Long remainderAmount);

    Optional<SettlementBatch> findByIdAndIsDeletedFalse(UUID id);
}