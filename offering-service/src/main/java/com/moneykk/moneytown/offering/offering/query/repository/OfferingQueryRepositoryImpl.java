package com.moneykk.moneytown.offering.offering.query.repository;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.CommonErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.entity.QOffering;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OfferingQueryRepositoryImpl implements OfferingQueryRepository {

    private static final QOffering offering = QOffering.offering;

    private static final List<OfferingStatus> PUBLIC_STATUSES = List.of(
            OfferingStatus.SCHEDULED,
            OfferingStatus.OPEN,
            OfferingStatus.SOLD_OUT,
            OfferingStatus.CLOSED
    );

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "startAt",
            "endAt",
            "pricePerUnit",
            "remainingQuantity"
    );

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Offering> searchPublicOfferings(
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        BooleanBuilder conditions = createCommonSearchConditions(condition);

        conditions.and(
                offering.offeringStatus.in(PUBLIC_STATUSES)
        );

        return search(conditions, pageable);
    }

    @Override
    public Page<Offering> searchMyOfferings(
            UUID issuerId,
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        BooleanBuilder conditions = createCommonSearchConditions(condition);

        conditions.and(
                offering.issuerId.eq(issuerId)
        );

        return search(conditions, pageable);
    }

    @Override
    public Page<Offering> searchOfferingsForManagement(
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        BooleanBuilder conditions = createCommonSearchConditions(condition);

        return search(conditions, pageable);
    }

    private Page<Offering> search(
            BooleanBuilder conditions,
            Pageable pageable
    ) {
        List<Offering> content = queryFactory
                .selectFrom(offering)
                .where(conditions)
                .orderBy(createOrderSpecifiers(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(offering.count())
                .from(offering)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(
                content,
                pageable,
                total != null ? total : 0L
        );
    }

    private BooleanBuilder createCommonSearchConditions(
            OfferingSearchCondition condition
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(
                offering.isDeleted.isFalse()
        );

        if (condition.offeringStatus() != null) {
            builder.and(
                    offering.offeringStatus.eq(
                            condition.offeringStatus()
                    )
            );
        }

        if (condition.keyword() != null
                && !condition.keyword().isBlank()) {
            builder.and(
                    offering.title.containsIgnoreCase(
                            condition.keyword().trim()
                    )
            );
        }

        return builder;
    }

    private OrderSpecifier<?>[] createOrderSpecifiers(
            Pageable pageable
    ) {
        if (pageable.getSort().isUnsorted()) {
            return new OrderSpecifier<?>[]{
                    offering.createdAt.desc(),
                    offering.offeringId.desc()
            };
        }

        List<OrderSpecifier<?>> orderSpecifiers =
                new ArrayList<>(
                        pageable.getSort()
                                .stream()
                                .map(this::toOrderSpecifier)
                                .toList()
                );

        /*
         * 동일한 정렬 값을 가진 공모가 여러 건 존재할 경우
         * offset 기반 페이징에서 항목 순서가 비결정적으로
         * 바뀌는 것을 방지한다.
         */
        orderSpecifiers.add(
                offering.offeringId.desc()
        );

        return orderSpecifiers.toArray(OrderSpecifier[]::new);
    }

    private OrderSpecifier<?> toOrderSpecifier(
            Sort.Order order
    ) {
        String property = order.getProperty();

        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_SORT_PROPERTY
            );
        }

        boolean ascending = order.isAscending();

        return switch (property) {
            case "createdAt" ->
                    ascending
                            ? offering.createdAt.asc()
                            : offering.createdAt.desc();

            case "startAt" ->
                    ascending
                            ? offering.startAt.asc()
                            : offering.startAt.desc();

            case "endAt" ->
                    ascending
                            ? offering.endAt.asc()
                            : offering.endAt.desc();

            case "pricePerUnit" ->
                    ascending
                            ? offering.pricePerUnit.asc()
                            : offering.pricePerUnit.desc();

            case "remainingQuantity" ->
                    ascending
                            ? offering.remainingQuantity.asc()
                            : offering.remainingQuantity.desc();

            default ->
                    throw new BusinessException(
                            CommonErrorCode.INVALID_SORT_PROPERTY
                    );
        };
    }
}