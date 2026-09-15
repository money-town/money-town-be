package com.moneykk.moneytown.offering.global.outbox;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OutboxErrorCode;
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
class OutboxRetryControllerTest {

    @Mock
    private OutboxRetryCommandService
            outboxRetryCommandService;

    @InjectMocks
    private OutboxRetryController
            outboxRetryController;

    @Test
    @DisplayName(
            "관리자의 Outbox 실패 이벤트 재처리 요청은 "
                    + "202 Accepted를 반환한다"
    )
    void returnsAcceptedForAdminRetryRequest() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        OutboxRetryResponse serviceResponse =
                OutboxRetryResponse.requeued(eventId);

        when(outboxRetryCommandService.retry(
                eventId,
                adminId,
                correlationId
        )).thenReturn(serviceResponse);

        // when
        ResponseEntity<
                ApiResponse<OutboxRetryResponse>
                > response =
                outboxRetryController.retryFailedEvent(
                        eventId,
                        adminId,
                        "ADMIN",
                        correlationId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody()).isNotNull();

        ApiResponse<OutboxRetryResponse> body =
                response.getBody();

        assertThat(body.success()).isTrue();

        assertThat(body.message())
                .isEqualTo(
                        "Outbox 이벤트 재처리 요청이 접수되었습니다."
                );

        assertThat(body.code()).isNull();
        assertThat(body.data()).isEqualTo(serviceResponse);

        assertThat(body.data().eventId())
                .isEqualTo(eventId);

        assertThat(body.data().eventStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        verify(outboxRetryCommandService)
                .retry(
                        eventId,
                        adminId,
                        correlationId
                );
    }

    @Test
    @DisplayName(
            "ADMIN 권한이 아니면 "
                    + "Outbox 이벤트 재처리 요청을 거부한다"
    )
    void rejectsRetryWhenUserIsNotAdmin() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        // when & then
        assertThatThrownBy(() ->
                outboxRetryController.retryFailedEvent(
                        eventId,
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
                                OutboxErrorCode
                                        .OUTBOX_RETRY_ACCESS_DENIED
                        )
                );

        verifyNoInteractions(outboxRetryCommandService);
    }
}