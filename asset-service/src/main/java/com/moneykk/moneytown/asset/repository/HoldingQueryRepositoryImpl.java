package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.dto.response.HoldingHistoryItemResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotItemResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.dto.response.MyHoldingItemResponse;
import com.moneykk.moneytown.asset.entity.*;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
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
public class HoldingQueryRepositoryImpl
        implements HoldingQueryRepository {

    private static final QHolding holding = QHolding.holding;
    private static final QHoldingHistory history = QHoldingHistory.holdingHistory;
    private static final QAsset asset = QAsset.asset;
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

    @Override
    public Optional<MyAssetHoldingResponse> findMyHolding(
            UUID assetId,
            UUID userId
    ) {
        // 자산과 로그인 사용자가 일치하는 보유지분 조회
        MyAssetHoldingResponse response = queryFactory
                .select(Projections.constructor(
                        MyAssetHoldingResponse.class,
                        holding.id,
                        holding.assetId,
                        holding.quantity,
                        holding.updatedAt
                ))
                .from(holding)
                .where(
                        holding.assetId.eq(assetId),
                        holding.userId.eq(userId)
                )
                .fetchOne();

        return Optional.ofNullable(response);
    }

    @Override
    public List<HoldingHistoryItemResponse> findHoldingHistories(
            UUID holdingId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        boolean ascending = direction.isAscending();
        BooleanExpression cursorCondition = null;

        if (cursor != null) {
            // 커서 이력의 등록 시간 조회
            Instant cursorCreatedAt = queryFactory
                    .select(history.createdAt)
                    .from(history)
                    .where(
                            history.id.eq(cursor),
                            history.holdingId.eq(holdingId)
                    )
                    .fetchOne();

            if (cursorCreatedAt == null) {
                throw new BusinessException(
                        AssetErrorCode.INVALID_HOLDING_CURSOR
                );
            }

            // 등록 시간과 ID를 함께 사용해 중복·누락 방지
            cursorCondition = ascending
                    ? history.createdAt.gt(cursorCreatedAt)
                    .or(history.createdAt.eq(cursorCreatedAt)
                            .and(history.id.gt(cursor)))
                    : history.createdAt.lt(cursorCreatedAt)
                    .or(history.createdAt.eq(cursorCreatedAt)
                            .and(history.id.lt(cursor)));
        }

        return queryFactory
                .select(Projections.constructor(
                        HoldingHistoryItemResponse.class,
                        history.id,
                        history.subscriptionId,
                        history.historyType,
                        history.quantity,
                        history.balanceBefore,
                        history.balanceAfter,
                        history.reason,
                        history.createdAt
                ))
                .from(history)
                .where(
                        history.holdingId.eq(holdingId),
                        cursorCondition
                )
                .orderBy(
                        ascending
                                ? history.createdAt.asc()
                                : history.createdAt.desc(),
                        ascending
                                ? history.id.asc()
                                : history.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    @Override
    public List<MyHoldingItemResponse> findMyHoldings(
            UUID userId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        boolean ascending = direction.isAscending();
        BooleanExpression cursorCondition = null;

        if (cursor != null) {
            // 내 보유지분에 해당하는 커서의 등록 시간 조회
            Instant cursorCreatedAt = queryFactory
                    .select(holding.createdAt)
                    .from(holding)
                    .where(
                            holding.id.eq(cursor),
                            holding.userId.eq(userId)
                    )
                    .fetchOne();

            if (cursorCreatedAt == null) {
                throw new BusinessException(
                        AssetErrorCode.INVALID_HOLDING_CURSOR
                );
            }

            // 등록 시간과 ID를 함께 사용해 중복·누락 방지
            cursorCondition = ascending
                    ? holding.createdAt.gt(cursorCreatedAt)
                    .or(holding.createdAt.eq(cursorCreatedAt)
                            .and(holding.id.gt(cursor)))
                    : holding.createdAt.lt(cursorCreatedAt)
                    .or(holding.createdAt.eq(cursorCreatedAt)
                            .and(holding.id.lt(cursor)));
        }

        return queryFactory
                .select(Projections.constructor(
                        MyHoldingItemResponse.class,
                        holding.id,
                        holding.assetId,
                        asset.assetName,
                        asset.type,
                        holding.quantity,
                        holding.updatedAt
                ))
                .from(holding)
                .join(asset)
                .on(holding.assetId.eq(asset.id))
                .where(
                        holding.userId.eq(userId),
                        holding.quantity.gt(0L),
                        asset.isDeleted.isFalse(),
                        cursorCondition
                )
                .orderBy(
                        ascending
                                ? holding.createdAt.asc()
                                : holding.createdAt.desc(),
                        ascending
                                ? holding.id.asc()
                                : holding.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<UUID> findUserIdByHoldingId(UUID holdingId) {
        // 보유지분의 소유자 조회
        UUID userId = queryFactory
                .select(holding.userId)
                .from(holding)
                .where(holding.id.eq(holdingId))
                .fetchOne();

        return Optional.ofNullable(userId);
    }

    @Override
    public Optional<Holding> findByIdForUpdate(UUID holdingId) {
        // 동시에 같은 지분을 변경하지 못하도록 잠금
        Holding result = queryFactory
                .selectFrom(holding)
                .where(holding.id.eq(holdingId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
