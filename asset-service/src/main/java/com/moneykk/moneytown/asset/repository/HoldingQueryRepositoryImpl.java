package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.Holding;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.SimplePath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HoldingQueryRepositoryImpl
        implements HoldingQueryRepository {

    private static final PathBuilder<Holding> holding = new PathBuilder<>(Holding.class, "holding");
    private static final SimplePath<UUID> holdingIdPath = holding.getSimple("id", UUID.class);
    private static final SimplePath<UUID> assetIdPath = holding.getSimple("assetId", UUID.class);

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<UUID> findAssetIdByHoldingId(UUID holdingId) {
        UUID assetId = queryFactory
                .select(assetIdPath)
                .from(holding)
                .where(holdingIdPath.eq(holdingId))
                .fetchOne();

        return Optional.ofNullable(assetId);
    }
}
