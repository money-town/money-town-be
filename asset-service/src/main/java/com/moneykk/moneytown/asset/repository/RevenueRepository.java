package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Revenue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 수익 저장 및 상태 변경 Repository */
public interface RevenueRepository extends JpaRepository<Revenue, UUID> {
}