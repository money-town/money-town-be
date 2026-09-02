package com.moneykk.moneytown.asset.repository;

import java.util.Optional;
import java.util.UUID;

public interface HoldingQueryRepository {

    Optional<UUID> findAssetIdByHoldingId(UUID holdingId);
}