package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.IssuerApplyRequest;
import com.moneykk.moneytown.user.dto.request.IssuerRejectRequest;
import com.moneykk.moneytown.user.dto.response.IssuerApplicationResponse;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import com.moneykk.moneytown.user.service.IssuerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "Issuer Application",
        description = "발행자 권한 신청·심사 API"
)
@RestController
@RequestMapping("/api/v1/issuer-applications")
@RequiredArgsConstructor
public class IssuerApplicationController {

    private final IssuerApplicationService issuerApplicationService;

    @Operation(summary = "발행자 권한 신청")
    @PostMapping
    public ApiResponse<IssuerApplicationResponse> apply(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Valid @RequestBody IssuerApplyRequest request
    ) {
        return ApiResponse.success(
                issuerApplicationService.apply(userId, request),
                "발행자 권한 신청 완료"
        );
    }

    @Operation(summary = "내 최근 발행자 권한 신청 조회")
    @GetMapping("/me/current")
    public ApiResponse<IssuerApplicationResponse> getCurrent(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId
    ) {
        return ApiResponse.success(
                issuerApplicationService.getCurrent(userId),
                "발행자 권한 신청 조회 성공"
        );
    }

    @Operation(summary = "발행자 권한 신청 심사 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<IssuerApplicationResponse>> getReviewList(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID adminId,
            @RequestParam(required = false) IssuerApplicationStatus status,
            @PageableDefault(size = 10, sort = "appliedAt") Pageable pageable
    ) {
        return ApiResponse.success(
                issuerApplicationService.getReviewList(
                        adminId,
                        status,
                        pageable
                ),
                "발행자 권한 신청 목록 조회 성공"
        );
    }

    @Operation(
            summary = "발행자 권한 신청 승인",
            description = "승인 후 변경된 ISSUER 권한을 사용하려면 재로그인 또는 토큰 재발급이 필요합니다."
    )
    @PatchMapping("/{applicationId}/approve")
    public ApiResponse<IssuerApplicationResponse> approve(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID adminId,
            @PathVariable UUID applicationId
    ) {
        return ApiResponse.success(
                issuerApplicationService.approve(adminId, applicationId),
                "발행자 권한 승인 완료. 변경된 권한을 사용하려면 재로그인 또는 토큰 재발급이 필요합니다."
        );
    }

    @Operation(summary = "발행자 권한 신청 거절")
    @PatchMapping("/{applicationId}/reject")
    public ApiResponse<IssuerApplicationResponse> reject(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID adminId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody IssuerRejectRequest request
    ) {
        return ApiResponse.success(
                issuerApplicationService.reject(
                        adminId,
                        applicationId,
                        request
                ),
                "발행자 권한 신청 거절 완료"
        );
    }
}
