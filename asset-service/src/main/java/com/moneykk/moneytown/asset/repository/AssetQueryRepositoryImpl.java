package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.QAsset;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssetQueryRepositoryImpl implements AssetQueryRepository {

    private static final QAsset asset = QAsset.asset;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Asset> findActiveById(UUID assetId) {
        // 삭제되지 않은 자산 조회
        Asset result = activeAssetQuery(assetId)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Asset> findActiveByIdForUpdate(UUID assetId) {
        // 자산을 잠그고 조회
        Asset result = activeAssetQuery(assetId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private JPAQuery<Asset> activeAssetQuery(UUID assetId) {
        // 자산 ID와 삭제 여부 조건 적용
        return queryFactory
                .selectFrom(asset)
                .where(
                        asset.id.eq(assetId),
                        asset.isDeleted.isFalse()
                );
    }
}
