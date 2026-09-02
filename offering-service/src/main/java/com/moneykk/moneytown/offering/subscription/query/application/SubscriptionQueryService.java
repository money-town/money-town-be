package com.moneykk.moneytown.offering.subscription.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.CommonErrorCode;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionDetailResponse;
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

    private final SubscriptionQueryRepository subscriptionQueryRepository;
    private final SubscriptionRepository subscriptionRepository;


    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt"
    );


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
     * 특정 청약의 상세 정보를 조회한다.
     *
     * INVESTOR는 본인의 청약만 조회할 수 있으며,
     * ADMIN은 전체 청약을 조회할 수 있다.
     */
    public SubscriptionDetailResponse getSubscriptionDetail(
            UUID subscriptionId,
            UUID userId,
            String role
    ) {
        Subscription subscription = subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId)
                .orElseThrow(() ->
                        new BusinessException(
                                SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                        )
                );

        validateDetailAccess(
                subscription,
                userId,
                role
        );

        return SubscriptionDetailResponse.from(subscription);
    }

    /**
     * 청약 상세 조회 권한을 검증한다.
     *
     * ADMIN은 전체 청약을 조회할 수 있고,
     * INVESTOR는 본인의 청약만 조회할 수 있다.
     */
    private void validateDetailAccess(
            Subscription subscription,
            UUID userId,
            String role
    ) {
        boolean admin =
                role != null
                        && "ADMIN".equalsIgnoreCase(role);

        boolean owner =
                userId != null
                        && subscription.getUserId().equals(userId);

        if (!admin && !owner) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
            );
        }
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

            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_SEARCH_CONDITION
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
                throw new BusinessException(
                        CommonErrorCode.INVALID_SORT_PROPERTY
                );
            }
        }
    }
}