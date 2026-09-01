package com.moneykk.moneytown.analysis.notification.query.repository;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.domain.QNotification;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationSearchCondition;
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
public class NotificationQueryRepositoryImpl implements NotificationQueryRepository{

    private static final QNotification notification = QNotification.notification;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Notification> search(NotificationSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = buildWhere(condition);

        List<Notification> content = queryFactory
                .selectFrom(notification)
                .where(where)
                .orderBy(notification.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(notification.count())
                .from(notification)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanBuilder buildWhere(NotificationSearchCondition c){
        BooleanBuilder where = new BooleanBuilder()
                .and(notification.isDeleted.isFalse());

        if(c.notificationType() != null){
            where.and(notification.notificationType.eq(c.notificationType()));
        }
        if(c.status() != null){
            where.and(notification.status.eq(c.status()));
        }
        if(c.userId() != null){
            where.and(notification.userId.eq(c.userId()));
        }
        return where;
    }
}
