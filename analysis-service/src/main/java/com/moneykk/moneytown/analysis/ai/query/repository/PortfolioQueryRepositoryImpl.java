package com.moneykk.moneytown.analysis.ai.query.repository;

import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.QPortfolio;
import com.moneykk.moneytown.analysis.ai.query.dto.PortfolioSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PortfolioQueryRepositoryImpl implements PortfolioQueryRepository{

    private static final QPortfolio portfolio = QPortfolio.portfolio;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Portfolio> searchMyPortfolio(UUID userId,PortfolioSearchCondition searchCondition, Pageable pageable) {
        BooleanBuilder search = searchPortfolios(searchCondition);

        search.and(portfolio.userId.eq(userId));

        List<Portfolio> content = queryFactory
                .selectFrom(portfolio)
                .where(search)
                .offset(pageable.getOffset())
                .orderBy(portfolio.createdAt.desc(), portfolio.id.desc())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(portfolio.count())
                .from(portfolio)
                .where(search)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Portfolio> search(PortfolioSearchCondition searchCondition, Pageable pageable) {

        BooleanBuilder search = searchPortfolios(searchCondition);

        List<Portfolio> content = queryFactory
                .selectFrom(portfolio)
                .where(search)
                .offset(pageable.getOffset())
                .orderBy(portfolio.createdAt.desc(), portfolio.id.desc())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(portfolio.count())
                .from(portfolio)
                .where(search)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);

    }

    private BooleanBuilder searchPortfolios(PortfolioSearchCondition searchCondition){
        BooleanBuilder where = new BooleanBuilder().and(portfolio.isDeleted.isFalse());

        if(searchCondition.aiStatus() != null){
            where.and(portfolio.status.eq(searchCondition.aiStatus()));
        }
        if(searchCondition.assetType() != null){
            where.and(portfolio.assetType.eq(searchCondition.assetType()));
        }
        if(searchCondition.riskType() != null){
            where.and(portfolio.riskType.eq(searchCondition.riskType()));
        }
        return where;
    }
}
