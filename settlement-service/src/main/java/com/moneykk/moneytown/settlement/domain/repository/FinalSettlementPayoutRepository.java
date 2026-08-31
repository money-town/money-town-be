package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinalSettlementPayoutRepository extends JpaRepository<FinalSettlementPayout, UUID> {
}