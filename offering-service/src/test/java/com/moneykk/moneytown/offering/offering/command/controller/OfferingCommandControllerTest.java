package com.moneykk.moneytown.offering.offering.command.controller;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCommandService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingStatusTransitionService;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}