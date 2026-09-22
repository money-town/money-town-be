package com.moneykk.moneytown.settlement.global.outbox;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Outbox 실패 이벤트 재처리 요청 결과.
 */
public record OutboxRetryResponse(

        @Schema(
                description = "재처리를 요청한 Outbox 이벤트 ID",
                example = "7cc970b5-53ae-4433-b3cc-80b523ef6801"
        )
        UUID eventId,

        @Schema(
                description = """
                        재처리 요청 접수 후 Outbox 이벤트 상태.
                        재처리가 접수되면 PENDING으로 반환됩니다.
                        """,
                example = "PENDING"
        )
        OutboxEventStatus eventStatus

) {

    /**
     * FAILED 이벤트가 PENDING으로 전환된 결과를 생성한다.
     */
    public static OutboxRetryResponse requeued(
            UUID eventId
    ) {
        return new OutboxRetryResponse(
                eventId,
                OutboxEventStatus.PENDING
        );
    }
}