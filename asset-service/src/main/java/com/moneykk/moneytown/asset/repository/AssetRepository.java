package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** 자산 Repository */
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    // ponytail: 자산 단위 락; 병목이 확인되면 조건부 UPDATE로 교체
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Asset> findByIdAndIsDeletedFalse(UUID assetId);
}
