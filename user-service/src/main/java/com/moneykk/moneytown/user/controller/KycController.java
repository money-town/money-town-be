package com.moneykk.moneytown.user.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.user.dto.request.KycApplyRequest;
import com.moneykk.moneytown.user.dto.request.KycApproveRequest;
import com.moneykk.moneytown.user.dto.request.KycRejectRequest;
import com.moneykk.moneytown.user.dto.response.KycResponse;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
import com.moneykk.moneytown.user.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "KYC",
        description = "KYC 신청·조회·심사 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/kyc-verifications")
@RequiredArgsConstructor
public class KycController {
    private final KycService kycService;

    @Operation(
            summary = "KYC 신청",
            description = "인증된 사용자가 KYC 심사를 신청"
    )
    @PostMapping
    public ApiResponse<KycResponse> apply(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @Valid @RequestBody KycApplyRequest request){


        return ApiResponse.success(kycService.apply(userId,request), "KYC 신청 완료");
    }


    @GetMapping("/me")
    public ApiResponse<List<KycResponse>> getHistory(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId){


        return ApiResponse.success(kycService.getHistory(userId),
                "KYC 이력 조회 성공");
    }

    @GetMapping("/me/current")
    public ApiResponse<KycResponse> getCurrent(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID)UUID userId){

        return ApiResponse.success(kycService.getCurrent(userId),
                "KYC 현재 상태 조회 성공");
    }

    // 관리자 KYC 심사 목록 조회
    @GetMapping
    public ApiResponse<PageResponse<KycResponse>> getReviewList(
            @RequestParam(required = false)
            KycVerificationStatus status,
            @PageableDefault(size = 10,
            sort = "submittedAt")
            Pageable pageable
    ){
        return ApiResponse.success(
                kycService.getReviewList(status,pageable),
                "KYC 심사 목록 조회 성공"
        );
    }

    // 관리자 KYC 심사 단건 조회
    @GetMapping("/{kycId}")
    public ApiResponse<KycResponse> getReview(
            @PathVariable("kycId") UUID kycId
    ){
        return ApiResponse.success(kycService.getReview(kycId),
                "KYC 심사 단건 조회 성공");


    }

    @Operation(
            summary = "KYC 심사 승인",
            description = "KYC 신청을 승인하고 만료 시각을 설정. ADMIN 권한 필요"
    )
    @PatchMapping("/{kycId}/approve")
    public ApiResponse<KycResponse> approve(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,

            @Parameter(description = "KYC 신청 ID", required = true)
            @PathVariable("kycId")
            UUID kycId
    ) {
        return ApiResponse.success(
                kycService.approve(adminId, kycId),
                "KYC 승인 완료");
    }

    // 관리자 KYC 거절
    @PatchMapping("/{kycId}/reject")
    public ApiResponse<KycResponse> reject(
            @Parameter(hidden = true)
            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,

            @PathVariable("kycId")
            UUID kycId,

            @Valid @RequestBody
            KycRejectRequest request
    ) {
        return ApiResponse.success(kycService.reject(adminId, kycId, request),
                "KYC 거절 완료");
    }




}
