package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HoldingSnapshotRepository extends JpaRepository<HoldingSnapshot, UUID> {
}