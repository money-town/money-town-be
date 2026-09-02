package com.moneykk.moneytown.offering.offering.query.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.query.application.OfferingQueryService;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingQueryController {

    private final OfferingQueryService offeringQueryService;

    /**
     * 공개 공모 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> searchPublicOfferings(
            @RequestParam(required = false) OfferingStatus offeringStatus,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        OfferingSearchCondition condition =
                new OfferingSearchCondition(
                        offeringStatus,
                        keyword
                );

        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchPublicOfferings(
                        condition,
                        pageable
                );

        String message = response.content().isEmpty()
                ? "조회된 공모가 없습니다."
                : "공개 공모 목록 조회가 완료되었습니다.";

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        message
                )
        );
    }

    /**
     * 내 공모 목록 조회
     */
    // TODO: Gateway Role 정책 반영 후 ISSUER 접근 제어 위치 최종 확인
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> searchMyOfferings(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestParam(required = false) OfferingStatus offeringStatus,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        OfferingSearchCondition condition =
                new OfferingSearchCondition(
                        offeringStatus,
                        keyword
                );

        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchMyOfferings(
                        userId,
                        condition,
                        pageable
                );

        String message = response.content().isEmpty()
                ? "조회된 공모가 없습니다."
                : "내 공모 목록 조회가 완료되었습니다.";

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        message
                )
        );
    }

    /**
     * 관리자 공모 목록 조회
     */
    // TODO: Gateway Role 정책 반영 후 ADMIN 접근 제어 위치 최종 확인
    @GetMapping("/manage")
    public ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> searchOfferingsForManagement(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam(required = false) OfferingStatus offeringStatus,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_MANAGEMENT_ACCESS_DENIED
            );
        }

        OfferingSearchCondition condition =
                new OfferingSearchCondition(
                        offeringStatus,
                        keyword
                );

        PageResponse<OfferingListItemResponse> response =
                offeringQueryService.searchOfferingsForManagement(
                        condition,
                        pageable
                );

        String message = response.content().isEmpty()
                ? "조회된 공모가 없습니다."
                : "관리자 공모 목록 조회가 완료되었습니다.";

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        message
                )
        );
    }

    /**
     * 공모 상품 상세 조회
     */
    @GetMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingDetailResponse>> getOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(
                    value = AuthHeaderConstants.USER_ID,
                    required = false
            ) UUID userId,
            @RequestHeader(
                    value = AuthHeaderConstants.USER_ROLE,
                    required = false
            ) String role
    ) {
        OfferingDetailResponse response =
                offeringQueryService.getOffering(
                        offeringId,
                        userId,
                        role
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 상품 상세 조회가 완료되었습니다."
                )
        );
    }
}