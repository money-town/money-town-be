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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "공모 명령",
        description = "공모 등록, 심사 및 상태 변경 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/offerings")
public class OfferingCommandController {

    private final OfferingCommandService offeringCommandService;
    private final OfferingStatusTransitionService offeringStatusTransitionService;

    @Operation(
            summary = "공모 상품 등록",
            description = "ISSUER가 공모 상품을 등록합니다. 등록된 공모는 DRAFT 상태로 생성됩니다."
    )
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

    @Operation(
            summary = "공모 심사 요청",
            description = "공모 소유자(ISSUER)가 DRAFT 상태의 공모를 REVIEW_REQUESTED 상태로 전환합니다."
    )
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

    @Operation(
            summary = "공모 승인",
            description = "ADMIN이 REVIEW_REQUESTED 상태의 공모를 승인하여 SCHEDULED 상태로 전환합니다."
    )
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

    @Operation(
            summary = "공모 반려",
            description = "ADMIN이 REVIEW_REQUESTED 상태의 공모를 사유와 함께 REJECTED 상태로 전환합니다."
    )
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

    @Operation(
            summary = "관리자 공모 중단",
            description = """
                    관리자가 공모를 중단합니다.
                    보상 대상이 없으면 즉시 취소하고,
                    보상이 필요하면 CANCELLING 상태로 전환하여
                    비동기 보상을 시작합니다.
                    """
    )
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

    @Operation(
            summary = "공모 상품 수정",
            description = "ISSUER 또는 ADMIN이 DRAFT 상태의 공모 상품 정보를 수정합니다."
    )
    @PatchMapping("/{offeringId}")
    public ResponseEntity<ApiResponse<OfferingUpdateResponse>> updateOffering(
            @PathVariable UUID offeringId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OfferingUpdateRequest request
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

    @Operation(
            summary = "공모 상품 삭제",
            description = "ISSUER 또는 ADMIN이 DRAFT 상태의 공모 상품을 논리 삭제합니다."
    )
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