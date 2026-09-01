package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** 보유지분 Repository */
public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    //기존 보유지분이 있으면 수량을 증가시킴
    Optional<Holding> findByAssetIdAndUserId(UUID assetId, UUID userId);
}
