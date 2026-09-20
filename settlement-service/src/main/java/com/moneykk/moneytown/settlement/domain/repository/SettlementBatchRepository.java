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

    Optional<SettlementBatch> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);
}