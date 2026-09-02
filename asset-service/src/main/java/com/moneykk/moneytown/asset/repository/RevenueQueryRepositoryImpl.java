package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.QRevenue;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}