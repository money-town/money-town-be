package com.moneykk.moneytown.analysis.fds.query.repository;

import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.domain.QFdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class FdsDetectionLogQueryRepositoryImpl implements FdsDetectionLogQueryRepository{

    private static final QFdsDetectionLog fdsDetectionLog = QFdsDetectionLog.fdsDetectionLog;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<FdsDetectionLog> search(FdsDetectionLogSearchCondition condition, Pageable pageable) {

        BooleanBuilder where = buildWhere(condition);

        List<FdsDetectionLog> content = queryFactory
                .selectFrom(fdsDetectionLog)
                .where(where)
                .orderBy(fdsDetectionLog.occurredAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(fdsDetectionLog.count())
                .from(fdsDetectionLog)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanBuilder buildWhere(FdsDetectionLogSearchCondition condition) {

        BooleanBuilder where = new BooleanBuilder();

        if(condition.userId() != null){
            where.and(fdsDetectionLog.userId.eq(condition.userId()));
        }
        if(condition.detectionType() != null){
            where.and(fdsDetectionLog.detectionType.eq(condition.detectionType()));
        }
        if(condition.ruleCode() != null){
            where.and(fdsDetectionLog.ruleCode.eq(condition.ruleCode()));
        }

        return where;
    }
}
