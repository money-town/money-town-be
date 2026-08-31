package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinalSettlementBatchRepository extends JpaRepository<FinalSettlementBatch, UUID> {
}