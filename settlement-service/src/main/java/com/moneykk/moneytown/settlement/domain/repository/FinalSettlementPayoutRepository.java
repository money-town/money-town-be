package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalSettlementPayoutRepository extends JpaRepository<FinalSettlementPayout, UUID> {

    Optional<FinalSettlementPayout> findByIdAndIsDeletedFalse(UUID id);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
            UUID finalSettlementBatchId, List<PayoutStatus> statuses);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndIsDeletedFalse(UUID finalSettlementBatchId);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(
            UUID finalSettlementBatchId, PayoutStatus status);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndIdInAndStatusAndIsDeletedFalse(
            UUID finalSettlementBatchId, List<UUID> ids, PayoutStatus status);
}