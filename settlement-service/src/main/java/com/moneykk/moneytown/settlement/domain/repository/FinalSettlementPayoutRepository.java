package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinalSettlementPayoutRepository extends JpaRepository<FinalSettlementPayout, UUID> {

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
            UUID finalSettlementBatchId, List<PayoutStatus> statuses);

    List<FinalSettlementPayout> findByFinalSettlementBatchIdAndIsDeletedFalse(UUID finalSettlementBatchId);
}