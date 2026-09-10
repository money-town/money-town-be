package com.moneykk.moneytown.offering.subscription.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.query.application.SubscriptionQueryService;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionDetailResponse;
import com.moneykk.moneytown.offering.subscription.query.dto.response.SubscriptionListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(
        name = "청약 조회",
        description = "청약 목록 및 상세 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionQueryController {

    private final SubscriptionQueryService subscriptionQueryService;

    @Operation(
            summary = "내 청약 목록 조회",
            description = "INVESTOR가 자신의 청약 목록을 공모, 상태 및 기간 조건으로 조회합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionListItemResponse>>> searchMySubscriptions(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam(required = false) UUID offeringId,
            @RequestParam(required = false) SubscriptionStatus subscriptionStatus,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @ParameterObject
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        if (!"INVESTOR".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
            );
        }

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

    @Operation(
            summary = "청약 상세 조회",
            description = "INVESTOR는 자신의 청약을 조회할 수 있으며, ADMIN은 전체 청약을 조회할 수 있습니다."
    )
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionDetailResponse>> getSubscriptionDetail(
            @PathVariable UUID subscriptionId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        boolean investor = "INVESTOR".equalsIgnoreCase(role);
        boolean admin = "ADMIN".equalsIgnoreCase(role);

        if (!investor && !admin) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_ACCESS_DENIED
            );
        }

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