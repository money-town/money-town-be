package com.moneykk.moneytown.offering.offering.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCommandService;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingCommandController {

    private final OfferingCommandService offeringCommandService;

    // TODO: Gateway/서비스 인가 정책 확정 후 ISSUER 권한 검증 적용
    @PostMapping
    public ResponseEntity<ApiResponse<OfferingCreateResponse>> createOffering(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody OfferingCreateRequest request
    ) {
        OfferingCreateResponse response =
                offeringCommandService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response,
                        "공모 상품 등록이 완료되었습니다."
                ));
    }

    // TODO: Gateway/서비스 인가 정책 확정 후 ISSUER 권한 검증 적용
    @PostMapping("/{offeringId}/review-requests")
    public ResponseEntity<ApiResponse<OfferingReviewRequestResponse>> requestReview(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        OfferingReviewRequestResponse response =
                offeringCommandService.requestReview(
                        offeringId,
                        userId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 심사 요청이 완료되었습니다."
                )
        );
    }

    // TODO: Gateway/서비스 인가 정책 확정 후 ADMIN 권한 검증 방식 재검토
    @PostMapping("/{offeringId}/approval")
    public ResponseEntity<ApiResponse<OfferingApprovalResponse>> approveOffering(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role
    ) {
        // TODO: OfferingException / OfferingErrorCode 적용 후 O005로 교체
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException(
                    "공모 승인 권한이 없습니다."
            );
        }

        OfferingApprovalResponse response =
                offeringCommandService.approveOffering(
                        offeringId,
                        userId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 승인이 완료되었습니다."
                )
        );
    }

    /**
     * 공모 반려
     */
    @PostMapping("/{offeringId}/rejection")
    public ResponseEntity<ApiResponse<OfferingRejectionResponse>> rejectOffering(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OfferingRejectionRequest request
    ) {
        // TODO: Gateway/서비스 인가 정책 확정 후 ADMIN 권한 검증 방식 재검토
        // TODO: OfferingException / OfferingErrorCode 적용 후 O005로 교체
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException(
                    "공모 심사 권한이 없습니다."
            );
        }

        OfferingRejectionResponse response =
                offeringCommandService.rejectOffering(
                        offeringId,
                        userId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 반려가 완료되었습니다."
                )
        );
    }

    /**
     * 공모 상품 수정
     */
    // TODO: Gateway/서비스 인가 정책 확정 후 권한 전달 방식 재검토
    @PatchMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingUpdateResponse>> updateOffering(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody OfferingUpdateRequest request
    ) {
        OfferingUpdateResponse response =
                offeringCommandService.updateOffering(
                        offeringId,
                        userId,
                        role,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 상품 수정이 완료되었습니다."
                )
        );
    }

    /**
     * 공모 상품 삭제
     */
    @DeleteMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingDeleteResponse>> deleteOffering(
            @PathVariable UUID offeringId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role
    ) {
        OfferingDeleteResponse response =
                offeringCommandService.deleteOffering(
                        offeringId,
                        userId,
                        role
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "공모 상품 삭제가 완료되었습니다."
                )
        );
    }
}