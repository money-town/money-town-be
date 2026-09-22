package com.moneykk.moneytown.settlement.global.outbox;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.global.exception.OutboxErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 관리자의 Outbox 실패 이벤트 재처리 API.
 */
@Tag(
        name = "Outbox 관리",
        description = "Settlement Service의 Outbox 실패 이벤트 운영 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlements/outbox-events")
public class OutboxRetryController {

    private final OutboxRetryCommandService
            outboxRetryCommandService;

    /**
     * 영구 발행 실패 상태인 Outbox 이벤트를
     * 다음 발행 스케줄에서 다시 처리할 수 있도록 대기 상태로 전환한다.
     */
    @Operation(
            summary = "Outbox 실패 이벤트 재처리",
            description = """
                    ADMIN이 Kafka 발행에 영구 실패한 Outbox 이벤트의
                    재처리를 요청합니다.
                    
                    FAILED 상태의 이벤트를 PENDING으로 전환하며,
                    실제 Kafka 발행은 다음 Outbox 발행 스케줄에서 수행됩니다.
                    
                    FAILED 상태가 아니거나 존재하지 않는 이벤트는
                    재처리할 수 없습니다.
                    """
    )
    @PostMapping("/{eventId}/retry")
    public ResponseEntity<
            ApiResponse<OutboxRetryResponse>
            > retryFailedEvent(
            @PathVariable UUID eventId,

            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID adminId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role,

            @RequestHeader(
                    value = AuthHeaderConstants.CORRELATION_ID,
                    required = false
            )
            String correlationId
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(
                    OutboxErrorCode.OUTBOX_RETRY_ACCESS_DENIED
            );
        }

        OutboxRetryResponse response =
                outboxRetryCommandService.retry(
                        eventId,
                        adminId,
                        correlationId
                );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.success(
                                response,
                                "Outbox 이벤트 재처리 요청이 접수되었습니다."
                        )
                );
    }
}