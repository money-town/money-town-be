package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
}