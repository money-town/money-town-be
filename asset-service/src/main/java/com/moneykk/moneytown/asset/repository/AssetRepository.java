package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** 자산 저장 저장소 */
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE p_assets
               SET allocated_quantity = allocated_quantity + :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE asset_id = :assetId
               AND asset_status = 'APPROVED'
               AND is_deleted = FALSE
               AND allocated_quantity + :quantity <= total_share_quantity
            """, nativeQuery = true)
    int allocateSharesAtomically(
            @Param("assetId") UUID assetId,
            @Param("quantity") long quantity
    );
}
