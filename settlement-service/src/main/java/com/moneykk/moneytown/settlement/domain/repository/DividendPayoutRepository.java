package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, UUID> {

    List<DividendPayout> findBySettlementBatchIdAndStatusAndIsDeletedFalse(UUID settlementBatchId, PayoutStatus status);
}