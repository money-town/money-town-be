package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** 보유지분 Repository */
public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    //기존 보유지분이 있으면 수량을 증가시킴
    Optional<Holding> findByAssetIdAndUserId(UUID assetId, UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO p_holdings (
                holding_id, asset_id, user_id, quantity, version,
                created_at, created_by, updated_at
            )
            VALUES (
                gen_random_uuid(), :assetId, :userId, 0, 0,
                CURRENT_TIMESTAMP, :userId, CURRENT_TIMESTAMP
            )
            ON CONFLICT (asset_id, user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("assetId") UUID assetId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT holding
            FROM Holding holding
            WHERE holding.assetId = :assetId
              AND holding.userId = :userId
            """)
    Optional<Holding> findByAssetIdAndUserIdForUpdate(
            @Param("assetId") UUID assetId,
            @Param("userId") UUID userId
    );
}
