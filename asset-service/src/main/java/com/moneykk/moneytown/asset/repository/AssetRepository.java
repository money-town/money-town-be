package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 자산 저장 저장소 */
public interface AssetRepository extends JpaRepository<Asset, UUID> {
}