package com.moneykk.moneytown.offering.subscription.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.query.application.SubscriptionQueryService;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionDetailResponse;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionQueryController {

    private final SubscriptionQueryService subscriptionQueryService;

    /**
     * 내 청약 목록 조회
     */
    // TODO: Gateway 인증/인가 정책 확정 후
    // INVESTOR 권한 및 사용자 정보 전달 방식 재검토
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionListItemResponse>>> searchMySubscriptions(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) UUID offeringId,
            @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            Pageable pageable
    ) {
        SubscriptionSearchCondition condition =
                new SubscriptionSearchCondition(
                        offeringId,
                        subscriptionStatus,
                        startDate,
                        endDate
                );

        PageResponse<SubscriptionListItemResponse> response =
                subscriptionQueryService.searchMySubscriptions(
                        userId,
                        condition,
                        pageable
                );

        String message = response.content().isEmpty()
                ? "조회된 청약 내역이 없습니다."
                : "내 청약 목록 조회가 완료되었습니다.";

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        message
                )
        );
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionDetailResponse>> getSubscriptionDetail(
            @PathVariable UUID subscriptionId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role
    ) {
        SubscriptionDetailResponse response =
                subscriptionQueryService.getSubscriptionDetail(
                        subscriptionId,
                        userId,
                        role
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "청약 상세 조회가 완료되었습니다."
                )
        );
    }
}