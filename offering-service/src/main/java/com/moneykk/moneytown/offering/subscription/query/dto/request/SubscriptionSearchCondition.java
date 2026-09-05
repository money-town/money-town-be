package com.moneykk.moneytown.offering.subscription.query.dto.request;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 내 청약 목록 검색 조건.
 *
 * 페이징 및 정렬 조건은 공통 Pageable을 통해 별도로 전달한다.
 * userId는 JWT 인증 정보에서 가져오므로 검색 조건에 포함하지 않는다.
 */
public record SubscriptionSearchCondition(

        /**
         * 특정 공모의 청약만 조회하기 위한 공모 ID.
         */
        UUID offeringId,

        /**
         * 조회할 청약 처리 상태.
         *
         * 지정하지 않으면 모든 청약 상태를 조회한다.
         */
        SubscriptionStatus subscriptionStatus,

        /**
         * 청약 접수 시작 시각.
         *
         * Subscription.createdAt을 기준으로 검색한다.
         */
        Instant startDate,

        /**
         * 청약 접수 종료 시각.
         *
         * Subscription.createdAt을 기준으로 검색한다.
         */
        Instant endDate

) {
}