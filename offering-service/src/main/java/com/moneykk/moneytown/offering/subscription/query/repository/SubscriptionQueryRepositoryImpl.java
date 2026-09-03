package com.moneykk.moneytown.offering.subscription.query.repository;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.CommonErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.moneykk.moneytown.offering.subscription.domain.entity.QSubscription.subscription;

@Repository
@RequiredArgsConstructor
public class SubscriptionQueryRepositoryImpl
        implements SubscriptionQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Subscription> searchMySubscriptions(
            UUID userId,
            SubscriptionSearchCondition condition,
            Pageable pageable
    ) {
        List<Subscription> content = queryFactory
                .selectFrom(subscription)
                .where(
                        subscription.userId.eq(userId),
                        subscription.isDeleted.isFalse(),
                        offeringIdEq(condition.offeringId()),
                        subscriptionStatusEq(condition.subscriptionStatus()),
                        createdAtGoe(condition.startDate()),
                        createdAtLoe(condition.endDate())
                )
                .orderBy(getOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(subscription.count())
                .from(subscription)
                .where(
                        subscription.userId.eq(userId),
                        subscription.isDeleted.isFalse(),
                        offeringIdEq(condition.offeringId()),
                        subscriptionStatusEq(condition.subscriptionStatus()),
                        createdAtGoe(condition.startDate()),
                        createdAtLoe(condition.endDate())
                )
                .fetchOne();

        return new PageImpl<>(
                content,
                pageable,
                total != null ? total : 0L
        );
    }

    /**
     * 특정 공모의 청약만 조회한다.
     */
    private BooleanExpression offeringIdEq(UUID offeringId) {
        return offeringId != null
                ? subscription.offeringId.eq(offeringId)
                : null;
    }

    /**
     * 특정 청약 처리 상태만 조회한다.
     */
    private BooleanExpression subscriptionStatusEq(
            SubscriptionStatus subscriptionStatus
    ) {
        return subscriptionStatus != null
                ? subscription.subscriptionStatus.eq(subscriptionStatus)
                : null;
    }

    /**
     * 청약 접수 시작 시각 이후의 청약을 조회한다.
     */
    private BooleanExpression createdAtGoe(Instant startDate) {
        return startDate != null
                ? subscription.createdAt.goe(startDate)
                : null;
    }

    /**
     * 청약 접수 종료 시각 이전의 청약을 조회한다.
     */
    private BooleanExpression createdAtLoe(Instant endDate) {
        return endDate != null
                ? subscription.createdAt.loe(endDate)
                : null;
    }

    /**
     * 지원하는 정렬 필드에 맞는 QueryDSL OrderSpecifier를 생성한다.
     *
     * 기본 정렬은 createdAt DESC이며,
     * 동일 createdAt 값이 존재하는 경우 안정적인 페이징을 위해
     * subscriptionId DESC를 보조 정렬 기준으로 사용한다.
     */
    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {

        if (pageable.getSort().isUnsorted()) {
            return new OrderSpecifier<?>[]{
                    subscription.createdAt.desc(),
                    subscription.subscriptionId.desc()
            };
        }

        List<OrderSpecifier<?>> orderSpecifiers =
                new ArrayList<>(
                        pageable.getSort()
                                .stream()
                                .map(order -> {
                                    Order direction = order.isAscending()
                                            ? Order.ASC
                                            : Order.DESC;

                    return switch (order.getProperty()) {
                        case "createdAt" ->
                                new OrderSpecifier<>(
                                        direction,
                                        subscription.createdAt
                                );

                        case "updatedAt" ->
                                new OrderSpecifier<>(
                                        direction,
                                        subscription.updatedAt
                                );

                        default ->
                                throw new BusinessException(
                                        CommonErrorCode.INVALID_SORT_PROPERTY
                                );
                    };
                })
                .toList()
        );

        /*
         * 동일한 createdAt / updatedAt 값을 가진 청약이 여러 건 존재할 경우
         * offset 기반 페이징에서 항목 순서가 비결정적으로 바뀌는 것을 방지한다.
         */
        orderSpecifiers.add(
                subscription.subscriptionId.desc()
        );

        return orderSpecifiers.toArray(OrderSpecifier[]::new);
    }
}