package com.moneykk.moneytown.offering.subscription.query.application;

import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionListItemResponse;
import com.moneykk.moneytown.offering.subscription.query.repository.SubscriptionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionQueryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt"
    );

    private final SubscriptionQueryRepository subscriptionQueryRepository;

    /**
     * 로그인한 투자자의 청약 목록을 검색한다.
     */
    public PageResponse<SubscriptionListItemResponse> searchMySubscriptions(
            UUID userId,
            SubscriptionSearchCondition condition,
            Pageable pageable
    ) {
        validateSearchCondition(condition);
        validateSort(pageable);

        Page<Subscription> subscriptions =
                subscriptionQueryRepository.searchMySubscriptions(
                        userId,
                        condition,
                        pageable
                );

        return PageResponse.from(
                subscriptions,
                SubscriptionListItemResponse::from
        );
    }

    /**
     * 청약 검색 기간의 유효성을 검증한다.
     *
     * startDate와 endDate가 모두 존재하는 경우
     * startDate가 endDate보다 이후일 수 없다.
     */
    private void validateSearchCondition(
            SubscriptionSearchCondition condition
    ) {
        Instant startDate = condition.startDate();
        Instant endDate = condition.endDate();

        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {

            // TODO: SubscriptionErrorCode 적용 후 S007로 변경
            throw new IllegalArgumentException(
                    "검색 조건이 유효하지 않습니다."
            );
        }
    }

    /**
     * 청약 목록에서 허용하는 정렬 필드를 검증한다.
     *
     * createdAt, updatedAt만 허용한다.
     */
    private void validateSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return;
        }

        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                // TODO: SubscriptionErrorCode 적용 후 S007로 변경
                throw new IllegalArgumentException(
                        "지원하지 않는 정렬 기준입니다: "
                                + order.getProperty()
                );
            }
        }
    }
}