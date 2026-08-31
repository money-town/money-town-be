package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, UUID> {

    List<DividendPayout> findBySettlementBatchIdAndStatusAndIsDeletedFalse(UUID settlementBatchId, PayoutStatus status);

    List<DividendPayout> findBySettlementBatchIdAndStatusInAndIsDeletedFalse(UUID settlementBatchId, List<PayoutStatus> statuses);

    List<DividendPayout> findBySettlementBatchIdAndIsDeletedFalse(UUID settlementBatchId);

    @Query("SELECT DISTINCT p.settlementBatchId FROM DividendPayout p WHERE p.status IN :statuses AND p.isDeleted = false")
    List<UUID> findDistinctSettlementBatchIdByStatusIn(@Param("statuses") List<PayoutStatus> statuses);
}