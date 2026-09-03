package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.QRevenue;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RevenueQueryRepositoryImpl implements RevenueQueryRepository {

    private static final QRevenue revenue = QRevenue.revenue;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Revenue> findByAssetIdAndRevenueId(
            UUID assetId,
            UUID revenueId
    ) {
        // 자산 ID와 수익 ID가 모두 일치하는 데이터 조회
        Revenue result = queryFactory
                .selectFrom(revenue)
                .where(
                        revenue.assetId.eq(assetId),
                        revenue.id.eq(revenueId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<Revenue> findReadyRevenues(UUID cursor, int limit, Sort.Direction direction) {
        boolean ascending = direction.isAscending();
        return queryFactory
                .selectFrom(revenue)
                .where(
                        revenue.transferStatus.eq(RevenueTransferStatus.READY),
                        cursorCondition(cursor, direction)
                )
                // 등록일과 ID를 같은 방향으로 정렬
                .orderBy(
                        ascending ? revenue.createdAt.asc() : revenue.createdAt.desc(),
                        ascending ? revenue.id.asc() : revenue.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Revenue> findByAssetId(
            UUID assetId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        boolean ascending = direction.isAscending();
        return queryFactory
                .selectFrom(revenue)
                .where(
                        revenue.assetId.eq(assetId),
                        cursorCondition(cursor, direction)
                )
                // 등록일과 ID를 같은 방향으로 정렬
                .orderBy(
                        ascending ? revenue.createdAt.asc() : revenue.createdAt.desc(),
                        ascending ? revenue.id.asc() : revenue.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    /**
     * 등록일 정렬 방향에 맞는 커서 조건
     */
    private BooleanExpression cursorCondition(UUID cursor, Sort.Direction direction) {
        if (cursor == null) {
            return null;
        }

        // 커서 수익의 등록 시간 조회
        Instant createdAt = queryFactory
                .select(revenue.createdAt)
                .from(revenue)
                .where(revenue.id.eq(cursor))
                .fetchOne();

        if (createdAt == null) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_REVENUE_CURSOR
            );
        }

        if (direction.isAscending()) {
            // 더 나중에 등록된 수익, 같은 시간이면 큰 ID
            return revenue.createdAt.gt(createdAt)
                    .or(revenue.createdAt.eq(createdAt).and(revenue.id.gt(cursor)));
        }

        // 더 오래된 수익, 같은 시간이면 작은 ID
        return revenue.createdAt.lt(createdAt)
                .or(
                        revenue.createdAt.eq(createdAt)
                                .and(revenue.id.lt(cursor))
                );
    }
}
