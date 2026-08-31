package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, UUID> {
}