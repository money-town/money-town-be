package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, UUID> {

    Optional<DividendPayout> findByIdAndIsDeletedFalse(UUID id);

    List<DividendPayout> findBySettlementBatchIdAndStatusAndIsDeletedFalse(UUID settlementBatchId, PayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DividendPayout> findBySettlementBatchIdAndStatusInAndIsDeletedFalse(UUID settlementBatchId, List<PayoutStatus> statuses);

    List<DividendPayout> findBySettlementBatchIdAndIsDeletedFalse(UUID settlementBatchId);

    @Query("SELECT DISTINCT p.settlementBatchId FROM DividendPayout p WHERE p.status IN :statuses AND p.isDeleted = false")
    List<UUID> findDistinctSettlementBatchIdByStatusIn(@Param("statuses") List<PayoutStatus> statuses);
}