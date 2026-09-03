package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.entity.QHolding;
import com.moneykk.moneytown.asset.entity.QHoldingHistory;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
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
public class HoldingQueryRepositoryImpl
        implements HoldingQueryRepository {

    private static final QHolding holding = QHolding.holding;
    private static final QHoldingHistory history = QHoldingHistory.holdingHistory;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<UUID> findAssetIdByHoldingId(UUID holdingId) {
        // 보유지분에 연결된 자산 ID 조회
        UUID assetId = queryFactory
                .select(holding.assetId)
                .from(holding)
                .where(holding.id.eq(holdingId))
                .fetchOne();

        return Optional.ofNullable(assetId);
    }

    @Override
    public List<HoldingSnapshotItemResponse> findSnapshotByAssetId(
            UUID assetId,
            Instant cutoffExclusive,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        // ALLOCATE는 더하고 REVOKE는 뺌
        NumberExpression<Long> balanceExpression =
                new CaseBuilder()
                        .when(history.historyType.eq(HoldingHistoryType.ALLOCATE))
                        .then(history.quantity)
                        .when(history.historyType.eq(HoldingHistoryType.REVOKE))
                        .then(history.quantity.negate())
                        .otherwise(0L)
                        .sum();

        boolean ascending = direction.isAscending();
        BooleanExpression cursorCondition = null;
        if (cursor != null) {
            // 보유지분 최초 등록 시간을 커서 기준으로 사용
            Instant createdAt = queryFactory.select(holding.createdAt)
                    .from(holding)
                    .where(holding.id.eq(cursor), holding.assetId.eq(assetId))
                    .fetchOne();
            if (createdAt == null) {
                throw new BusinessException(AssetErrorCode.INVALID_HOLDING_CURSOR);
            }
            cursorCondition = ascending
                    ? holding.createdAt.gt(createdAt)
                            .or(holding.createdAt.eq(createdAt).and(holding.id.gt(cursor)))
                    : holding.createdAt.lt(createdAt)
                            .or(holding.createdAt.eq(createdAt).and(holding.id.lt(cursor)));
        }

        return queryFactory
                .select(Projections.constructor(
                        HoldingSnapshotItemResponse.class,
                        holding.id,
                        holding.userId,
                        balanceExpression
                ))
                .from(history)
                .join(holding)
                .on(history.holdingId.eq(holding.id))
                .where(
                        holding.assetId.eq(assetId),
                        history.createdAt.lt(cutoffExclusive),
                        history.historyType.in(
                                HoldingHistoryType.ALLOCATE,
                                HoldingHistoryType.REVOKE
                        ),
                        cursorCondition
                )
                .groupBy(
                        holding.id,
                        holding.userId,
                        holding.createdAt
                )
                // 기준일 보유 수량이 0이면 제외
                .having(balanceExpression.gt(0L))
                .orderBy(
                        ascending ? holding.createdAt.asc() : holding.createdAt.desc(),
                        ascending ? holding.id.asc() : holding.id.desc()
                )
                .limit(limit)
                .fetch();
    }
}
