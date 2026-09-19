package com.moneykk.moneytown.offering.offering.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCommandService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingApprovalResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingRejectionResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCommandControllerTest {

    @Mock
    private OfferingCommandService offeringCommandService;

    @Mock
    private OfferingStatusTransitionService
            offeringStatusTransitionService;

    @InjectMocks
    private OfferingCommandController offeringCommandController;

    // 즉시 취소되는 경우의 HTTP 200 응답 계약을 검증한다.
    @Test
    @DisplayName("보상 대상이 없어 공모가 즉시 취소되면 200 OK를 반환한다")
    void returnsOkWhenOfferingIsCancelledImmediately() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        OfferingCancellationResponse serviceResponse =
                new OfferingCancellationResponse(
                        offeringId,
                        OfferingStatus.CANCELLED
                );

        when(offeringStatusTransitionService.cancelByAdmin(
                offeringId,
                correlationId
        )).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingCancellationResponse>> response =
                offeringCommandController.cancelOfferingByAdmin(
                        offeringId,
                        adminId,
                        "ADMIN",
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<OfferingCancellationResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo("공모가 취소되었습니다.");
        assertThat(body.code()).isNull();

        assertThat(body.data()).isEqualTo(serviceResponse);
        assertThat(body.data().offeringId())
                .isEqualTo(offeringId);
        assertThat(body.data().offeringStatus())
                .isEqualTo(OfferingStatus.CANCELLED);

        verify(offeringStatusTransitionService)
                .cancelByAdmin(
                        offeringId,
                        correlationId
                );

        verifyNoInteractions(offeringCommandService);
    }

    // 비동기 보상이 필요한 경우의 HTTP 202 응답 계약을 검증한다.
    @Test
    @DisplayName("보상이 필요한 공모 중단 요청은 202 Accepted를 반환한다")
    void returnsAcceptedWhenOfferingCompensationIsRequired() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        OfferingCancellationResponse serviceResponse =
                new OfferingCancellationResponse(
                        offeringId,
                        OfferingStatus.CANCELLING
                );

        when(offeringStatusTransitionService.cancelByAdmin(
                offeringId,
                correlationId
        )).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingCancellationResponse>> response =
                offeringCommandController.cancelOfferingByAdmin(
                        offeringId,
                        adminId,
                        "ADMIN",
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<OfferingCancellationResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();
        assertThat(body.message())
                .isEqualTo("공모 중단 요청이 접수되었습니다.");
        assertThat(body.code()).isNull();

        assertThat(body.data()).isEqualTo(serviceResponse);
        assertThat(body.data().offeringId())
                .isEqualTo(offeringId);
        assertThat(body.data().offeringStatus())
                .isEqualTo(OfferingStatus.CANCELLING);

        verify(offeringStatusTransitionService)
                .cancelByAdmin(
                        offeringId,
                        correlationId
                );

        verifyNoInteractions(offeringCommandService);
    }

    // ADMIN이 아닌 사용자는 서비스 호출 전에 차단되는지 검증한다.
    @Test
    @DisplayName("ADMIN 권한이 아니면 공모 중단을 거부한다")
    void rejectsCancellationWhenUserIsNotAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.cancelOfferingByAdmin(
                        offeringId,
                        userId,
                        "ISSUER",
                        correlationId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                OfferingErrorCode
                                        .OFFERING_MANAGEMENT_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(
                offeringCommandService,
                offeringStatusTransitionService
        );
    }

    @Test
    @DisplayName("ISSUER가 공모 상품을 등록하면 201 Created를 반환한다")
    void createsOfferingForIssuer() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingCreateRequest request = new OfferingCreateRequest(
                UUID.randomUUID(),
                10000L,
                10L,
                1000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(8)
        );

        OfferingCreateResponse serviceResponse = new OfferingCreateResponse(
                UUID.randomUUID(),
                request.assetId(),
                "강남 오피스텔 조각투자 1차 공모",
                OfferingStatus.DRAFT,
                request.totalQuantity(),
                request.totalQuantity(),
                Instant.now()
        );

        when(offeringCommandService.create(issuerId, request))
                .thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingCreateResponse>> response =
                offeringCommandController.createOffering(
                        issuerId, "ISSUER", request
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 상품 등록이 완료되었습니다.");

        verify(offeringCommandService).create(issuerId, request);
        verifyNoInteractions(offeringStatusTransitionService);
    }

    @Test
    @DisplayName("ISSUER가 아니면 공모 상품 등록을 거부한다")
    void rejectsCreateOfferingWhenUserIsNotIssuer() {
        // given
        UUID userId = UUID.randomUUID();
        OfferingCreateRequest request = new OfferingCreateRequest(
                UUID.randomUUID(),
                10000L,
                10L,
                1000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(8)
        );

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.createOffering(
                        userId, "ADMIN", request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED)
                );

        verifyNoInteractions(
                offeringCommandService,
                offeringStatusTransitionService
        );
    }

    @Test
    @DisplayName("ISSUER가 공모 심사를 요청하면 200 OK를 반환한다")
    void requestsReviewForIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        OfferingReviewRequestResponse serviceResponse =
                new OfferingReviewRequestResponse(
                        offeringId,
                        OfferingStatus.REVIEW_REQUESTED,
                        Instant.now()
                );

        when(offeringCommandService.requestReview(offeringId, issuerId))
                .thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingReviewRequestResponse>> response =
                offeringCommandController.requestReview(
                        offeringId, issuerId, "ISSUER"
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 심사 요청이 완료되었습니다.");

        verify(offeringCommandService).requestReview(offeringId, issuerId);
    }

    @Test
    @DisplayName("ISSUER가 아니면 공모 심사 요청을 거부한다")
    void rejectsRequestReviewWhenUserIsNotIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.requestReview(
                        offeringId, userId, "ADMIN"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringCommandService);
    }

    @Test
    @DisplayName("ADMIN이 공모를 승인하면 200 OK를 반환한다")
    void approvesOfferingForAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        OfferingApprovalResponse serviceResponse = new OfferingApprovalResponse(
                offeringId,
                OfferingStatus.SCHEDULED,
                Instant.now(),
                adminId
        );

        when(offeringCommandService.approveOffering(offeringId, adminId))
                .thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingApprovalResponse>> response =
                offeringCommandController.approveOffering(
                        offeringId, adminId, "ADMIN"
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 승인이 완료되었습니다.");

        verify(offeringCommandService).approveOffering(offeringId, adminId);
    }

    @Test
    @DisplayName("ADMIN이 아니면 공모 승인을 거부한다")
    void rejectsApprovalWhenUserIsNotAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.approveOffering(
                        offeringId, userId, "ISSUER"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_REVIEW_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringCommandService);
    }

    @Test
    @DisplayName("ADMIN이 공모를 반려하면 200 OK를 반환한다")
    void rejectsOfferingForAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        OfferingRejectionRequest request =
                new OfferingRejectionRequest("자산 증빙 자료가 충분하지 않습니다.");

        OfferingRejectionResponse serviceResponse = new OfferingRejectionResponse(
                offeringId,
                OfferingStatus.REJECTED,
                request.rejectionReason(),
                Instant.now(),
                adminId
        );

        when(offeringCommandService.rejectOffering(offeringId, adminId, request))
                .thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingRejectionResponse>> response =
                offeringCommandController.rejectOffering(
                        offeringId, adminId, "ADMIN", request
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 반려가 완료되었습니다.");

        verify(offeringCommandService)
                .rejectOffering(offeringId, adminId, request);
    }

    @Test
    @DisplayName("ADMIN이 아니면 공모 반려를 거부한다")
    void rejectsRejectionWhenUserIsNotAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OfferingRejectionRequest request =
                new OfferingRejectionRequest("사유");

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.rejectOffering(
                        offeringId, userId, "ISSUER", request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_REVIEW_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringCommandService);
    }

    @Test
    @DisplayName("ISSUER 또는 ADMIN이 공모 상품을 수정하면 200 OK를 반환한다")
    void updatesOfferingForIssuerOrAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        OfferingUpdateRequest request = new OfferingUpdateRequest(
                "변경된 상품명", 12000L, 10L, 1000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(8)
        );

        OfferingUpdateResponse serviceResponse = new OfferingUpdateResponse(
                offeringId, "변경된 상품명", 10000L, 12000L, 12000L,
                10L, 1000L, Instant.now(), Instant.now(),
                OfferingStatus.DRAFT, Instant.now()
        );

        when(offeringCommandService.updateOffering(
                offeringId, issuerId, "ISSUER", request
        )).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingUpdateResponse>> response =
                offeringCommandController.updateOffering(
                        offeringId, issuerId, "ISSUER", request
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 상품 수정이 완료되었습니다.");

        verify(offeringCommandService)
                .updateOffering(offeringId, issuerId, "ISSUER", request);
    }

    @Test
    @DisplayName("ISSUER와 ADMIN이 아니면 공모 상품 수정을 거부한다")
    void rejectsUpdateWhenUserIsNeitherIssuerNorAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OfferingUpdateRequest request = new OfferingUpdateRequest(
                null, null, null, null, null, null
        );

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.updateOffering(
                        offeringId, userId, "VIEWER", request
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringCommandService);
    }

    @Test
    @DisplayName("ISSUER 또는 ADMIN이 공모 상품을 삭제하면 200 OK를 반환한다")
    void deletesOfferingForIssuerOrAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        OfferingDeleteResponse serviceResponse =
                new OfferingDeleteResponse(offeringId);

        when(offeringCommandService.deleteOffering(
                offeringId, adminId, "ADMIN"
        )).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<OfferingDeleteResponse>> response =
                offeringCommandController.deleteOffering(
                        offeringId, adminId, "ADMIN"
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        assertThat(response.getBody().message())
                .isEqualTo("공모 상품 삭제가 완료되었습니다.");

        verify(offeringCommandService)
                .deleteOffering(offeringId, adminId, "ADMIN");
    }

    @Test
    @DisplayName("ISSUER와 ADMIN이 아니면 공모 상품 삭제를 거부한다")
    void rejectsDeleteWhenUserIsNeitherIssuerNorAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() ->
                offeringCommandController.deleteOffering(
                        offeringId, userId, "VIEWER"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED)
                );

        verifyNoInteractions(offeringCommandService);
    }
}