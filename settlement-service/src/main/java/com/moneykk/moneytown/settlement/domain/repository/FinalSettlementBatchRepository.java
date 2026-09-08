package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalSettlementBatchRepository extends JpaRepository<FinalSettlementBatch, UUID> {

    Optional<FinalSettlementBatch> findByAssetIdAndIsDeletedFalse(UUID assetId);

    Optional<FinalSettlementBatch> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);

    List<FinalSettlementBatch> findByStatusAndAssetTerminationCompletedAtIsNullAndIsDeletedFalse(SettlementStatus status);
}