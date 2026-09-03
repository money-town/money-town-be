package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 수익 저장 및 상태 변경 Repository */
public interface RevenueRepository extends JpaRepository<Revenue, UUID> {

    // 같은 자산에서 동일한 원본 수익이 등록됐는지 확인
    boolean existsByAssetIdAndSourceTypeAndSourceReferenceId(
            UUID assetId,
            RevenueSourceType sourceType,
            String sourceReferenceId
    );
}