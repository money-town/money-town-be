package com.moneykk.moneytown.settlement.global.outbox;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.global.exception.OutboxErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 관리자의 Outbox 실패 이벤트 재처리 요청을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRetryCommandService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublishService outboxPublishService;

    /**
     * FAILED 상태의 Outbox 이벤트를 PENDING으로 전환한다.
     *
     * 실제 Kafka 발행은 다음 Outbox 발행 스케줄에서 수행된다.
     *
     * @param eventId 재처리할 Outbox 이벤트 ID
     * @param adminId 재처리를 요청한 관리자 ID
     * @param correlationId 요청 추적 ID
     * @return 재처리 요청 결과
     */
    @Transactional
    public OutboxRetryResponse retry(
            UUID eventId,
            UUID adminId,
            String correlationId
    ) {
        Objects.requireNonNull(
                eventId,
                "eventId는 필수입니다."
        );

        Objects.requireNonNull(
                adminId,
                "adminId는 필수입니다."
        );

        OutboxEvent event = outboxEventRepository
                .findById(eventId)
                .orElseThrow(
                        () -> new BusinessException(
                                OutboxErrorCode.OUTBOX_EVENT_NOT_FOUND
                        )
                );

        if (event.getEventStatus()
                != OutboxEventStatus.FAILED) {
            throw new BusinessException(
                    OutboxErrorCode.OUTBOX_RETRY_NOT_ALLOWED
            );
        }

        /*
         * 상태 확인 후 다른 관리자 요청이 먼저 처리될 수 있으므로
         * 조건부 UPDATE 결과도 다시 확인한다.
         */
        boolean requeued =
                outboxPublishService.requeueFailedEvent(
                        eventId
                );

        if (!requeued) {
            throw new BusinessException(
                    OutboxErrorCode.OUTBOX_RETRY_NOT_ALLOWED
            );
        }

        log.info(
                "Outbox 실패 이벤트 재처리 요청 접수. "
                        + "eventId={}, adminId={}, correlationId={}",
                eventId,
                adminId,
                correlationId
        );

        return OutboxRetryResponse.requeued(eventId);
    }
}