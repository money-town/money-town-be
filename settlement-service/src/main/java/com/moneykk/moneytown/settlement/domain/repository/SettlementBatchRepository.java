package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {
}