package com.moneykk.moneytown.offering.offering.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCommandService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.*;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
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
    private final OfferingStatusTransitionService offeringStatusTransitionService;

    /**
     * 공모 상품 등록
     *
     * ISSUER만 접근할 수 있다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OfferingCreateResponse>> createOffering(
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OfferingCreateRequest request
    ) {
        if (!"ISSUER".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        OfferingCreateResponse response =
                offeringCommandService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response,
                        "공모 상품 등록이 완료되었습니다."
                ));
    }

    /**
     * 공모 심사 요청
     *
     * ISSUER만 접근할 수 있다.
     * 실제 공모 소유자 여부는 Service에서 추가 검증한다.
     */
    @PostMapping("/{offeringId}/review-requests")
    public ResponseEntity<ApiResponse<OfferingReviewRequestResponse>> requestReview(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {

        if (!"ISSUER".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

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

    /**
     * 공모 승인
     *
     * ADMIN만 접근할 수 있다.
     */
    @PostMapping("/{offeringId}/approval")
    public ResponseEntity<ApiResponse<OfferingApprovalResponse>> approveOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_REVIEW_ACCESS_DENIED
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
     *
     * ADMIN만 접근할 수 있다.
     */
    @PostMapping("/{offeringId}/rejection")
    public ResponseEntity<ApiResponse<OfferingRejectionResponse>> rejectOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OfferingRejectionRequest request
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_REVIEW_ACCESS_DENIED
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
     * 관리자 공모 긴급 중단
     *
     * ADMIN만 접근할 수 있다.
     * 보상 대상 또는 미해결 청약이 없으면 즉시 취소를 완료하고,
     * 보상이 필요하면 CANCELLING 상태로 비동기 처리를 시작한다.
     */
    @PostMapping("/{offeringId}/cancellation")
    public ResponseEntity<ApiResponse<OfferingCancellationResponse>>
    cancelOfferingByAdmin(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestHeader(AuthHeaderConstants.CORRELATION_ID)
            String correlationId
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_MANAGEMENT_ACCESS_DENIED
            );
        }

        /*
         * userId는 JpaAuditingConfig가 요청 헤더에서 읽어
         * updatedBy에 관리자 ID를 기록할 때 사용한다.
         */
        OfferingCancellationResponse response =
                offeringStatusTransitionService.cancelByAdmin(
                        offeringId,
                        correlationId
                );

        if (response.offeringStatus() == OfferingStatus.CANCELLED) {
            return ResponseEntity.ok(
                    ApiResponse.success(
                            response,
                            "공모가 취소되었습니다."
                    )
            );
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.success(
                                response,
                                "공모 중단 요청이 접수되었습니다."
                        )
                );
    }

    /**
     * 공모 상품 수정
     *
     * ISSUER 또는 ADMIN만 접근할 수 있다.
     * ISSUER의 실제 공모 소유권은 Service에서 추가 검증한다.
     */
    @PatchMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingUpdateResponse>> updateOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestBody OfferingUpdateRequest request
    ) {

        boolean issuer = "ISSUER".equalsIgnoreCase(role);
        boolean admin = "ADMIN".equalsIgnoreCase(role);

        if (!issuer && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

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
     *
     * ISSUER 또는 ADMIN만 접근할 수 있다.
     * ISSUER의 실제 공모 소유권은 Service에서 추가 검증한다.
     */
    @DeleteMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingDeleteResponse>> deleteOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {

        boolean issuer = "ISSUER".equalsIgnoreCase(role);
        boolean admin = "ADMIN".equalsIgnoreCase(role);

        if (!issuer && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

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