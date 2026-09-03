package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.QRevenue;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<Revenue> findReadyRevenues(
            UUID cursor,
            int limit
    ) {
        // READY 상태 수익을 UUID 순서로 조회
        return queryFactory
                .selectFrom(revenue)
                .where(
                        revenue.transferStatus.eq(RevenueTransferStatus.READY),

                        // 첫 요청은 cursor가 없으므로 조건을 적용하지 않음
                        cursor == null ? null : revenue.id.gt(cursor)
                )
                .orderBy(revenue.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Revenue> findByAssetId(
            UUID assetId,
            UUID cursor,
            int limit
    ) {
        return queryFactory
                .selectFrom(revenue)
                .where(
                        // 해당 자산의 수익만 조회
                        revenue.assetId.eq(assetId),

                        // 다음 페이지는 마지막 수익 ID 이후부터 조회
                        cursor == null ? null : revenue.id.gt(cursor)
                )
                // 커서 조건과 같은 순서로 정렬
                .orderBy(revenue.id.asc())
                .limit(limit)
                .fetch();
    }
}