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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
        name = "공모 조회",
        description = "공모 목록 및 상세 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingQueryController {

    private final OfferingQueryService offeringQueryService;

    @Operation(
            summary = "공개 공모 목록 조회",
            description = "인증 없이 공개된 공모 목록을 상태와 검색어 조건으로 조회합니다."
    )
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

    @Operation(
            summary = "내 공모 목록 조회",
            description = "ISSUER가 자신이 등록한 공모 목록을 상태와 검색어 조건으로 조회합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<OfferingListItemResponse>>> searchMyOfferings(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam(required = false) OfferingStatus offeringStatus,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {

        if (!"ISSUER".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

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

    @Operation(
            summary = "관리자 공모 목록 조회",
            description = "ADMIN이 전체 공모 목록을 상태와 검색어 조건으로 조회합니다."
    )
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

    @Operation(
            summary = "공모 상품 상세 조회",
            description = """
                공개 상태의 공모는 인증 없이 조회할 수 있습니다.
                비공개 상태의 공모는 공모 소유자 또는 ADMIN만 조회할 수 있습니다.
                """
    )
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