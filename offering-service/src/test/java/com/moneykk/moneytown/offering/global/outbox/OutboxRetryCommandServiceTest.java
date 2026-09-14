package com.moneykk.moneytown.offering.global.outbox;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OutboxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRetryCommandServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxPublishService outboxPublishService;

    @Mock
    private OutboxEvent outboxEvent;

    @InjectMocks
    private OutboxRetryCommandService service;

    @Test
    @DisplayName("FAILED Outbox 이벤트를 PENDING 상태로 재등록한다")
    void requeuesFailedEvent() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String correlationId = "correlation-id";

        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        when(outboxEvent.getEventStatus())
                .thenReturn(OutboxEventStatus.FAILED);

        when(outboxPublishService.requeueFailedEvent(eventId))
                .thenReturn(true);

        // when
        OutboxRetryResponse response = service.retry(
                eventId,
                adminId,
                correlationId
        );

        // then
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.eventStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        verify(outboxEventRepository)
                .findById(eventId);

        verify(outboxPublishService)
                .requeueFailedEvent(eventId);
    }

    @Test
    @DisplayName("존재하지 않는 Outbox 이벤트는 재처리할 수 없다")
    void rejectsMissingEvent() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
                        eventId,
                        adminId,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OutboxErrorCode.OUTBOX_EVENT_NOT_FOUND
                );

        verifyNoInteractions(outboxPublishService);
    }

    @ParameterizedTest
    @EnumSource(
            value = OutboxEventStatus.class,
            names = "FAILED",
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("FAILED 상태가 아닌 Outbox 이벤트는 재처리할 수 없다")
    void rejectsEventThatIsNotFailed(
            OutboxEventStatus eventStatus
    ) {
        // given
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        when(outboxEvent.getEventStatus())
                .thenReturn(eventStatus);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
                        eventId,
                        adminId,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OutboxErrorCode.OUTBOX_RETRY_NOT_ALLOWED
                );

        verify(outboxPublishService, never())
                .requeueFailedEvent(eventId);
    }

    @Test
    @DisplayName("동시에 먼저 재등록된 Outbox 이벤트는 중복 처리하지 않는다")
    void rejectsWhenConditionalUpdateFails() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        when(outboxEvent.getEventStatus())
                .thenReturn(OutboxEventStatus.FAILED);

        /*
         * 조회 이후 다른 요청이 먼저 FAILED → PENDING으로
         * 변경한 상황을 나타낸다.
         */
        when(outboxPublishService.requeueFailedEvent(eventId))
                .thenReturn(false);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
                        eventId,
                        adminId,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OutboxErrorCode.OUTBOX_RETRY_NOT_ALLOWED
                );

        verify(outboxPublishService)
                .requeueFailedEvent(eventId);
    }
}