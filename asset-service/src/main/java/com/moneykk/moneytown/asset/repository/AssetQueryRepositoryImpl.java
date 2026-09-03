package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.QAsset;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    @Override
    public List<Asset> findAssets(
            UUID ownerId,
            AssetStatus status,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        boolean ascending = direction.isAscending();
        BooleanExpression cursorCondition = null;

        if (cursor != null) {
            // 커서 자산의 등록 시간 조회
            Instant cursorCreatedAt = queryFactory
                    .select(asset.createdAt)
                    .from(asset)
                    .where(asset.id.eq(cursor))
                    .fetchOne();

            if (cursorCreatedAt == null) {
                throw new BusinessException(
                        AssetErrorCode.INVALID_ASSET_CURSOR
                );
            }

            if (ascending) {
                // 오름차순: 커서보다 나중에 등록된 자산
                cursorCondition = asset.createdAt.gt(cursorCreatedAt)
                        .or(
                                asset.createdAt.eq(cursorCreatedAt)
                                        .and(asset.id.gt(cursor))
                        );
            } else {
                // 내림차순: 커서보다 먼저 등록된 자산
                cursorCondition = asset.createdAt.lt(cursorCreatedAt)
                        .or(
                                asset.createdAt.eq(cursorCreatedAt)
                                        .and(asset.id.lt(cursor))
                        );
            }
        }

        return queryFactory
                .selectFrom(asset)
                .where(
                        // 삭제된 자산 제외
                        asset.isDeleted.isFalse(),

                        // 조회 범위 제한
                        ownerId == null ? null : asset.userId.eq(ownerId),
                        status == null ? null : asset.status.eq(status),

                        cursorCondition
                )
                // 등록 시간이 같으면 ID도 같은 방향으로 정렬
                .orderBy(
                        ascending ? asset.createdAt.asc() : asset.createdAt.desc(),
                        ascending ? asset.id.asc() : asset.id.desc()
                )
                .limit(limit)
                .fetch();
    }
}
