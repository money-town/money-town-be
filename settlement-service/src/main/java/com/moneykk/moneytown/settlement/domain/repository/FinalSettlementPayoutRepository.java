package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalSettlementPayoutRepository extends JpaRepository<FinalSettlementPayout, UUID> {

    Optional<FinalSettlementPayout> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT DISTINCT p.finalSettlementBatchId FROM FinalSettlementPayout p WHERE p.status IN :statuses AND p.isDeleted = false")
    List<UUID> findDistinctFinalSettlementBatchIdByStatusIn(@Param("statuses") List<PayoutStatus> statuses);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
            UUID finalSettlementBatchId, List<PayoutStatus> statuses);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndIsDeletedFalse(UUID finalSettlementBatchId);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(
            UUID finalSettlementBatchId, PayoutStatus status);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndIdInAndStatusAndIsDeletedFalse(
            UUID finalSettlementBatchId, List<UUID> ids, PayoutStatus status);
}