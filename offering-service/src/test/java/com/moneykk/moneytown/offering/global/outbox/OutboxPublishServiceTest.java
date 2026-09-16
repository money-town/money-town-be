package com.moneykk.moneytown.offering.global.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxPublishService outboxPublishService;

    @Test
    @DisplayName("DB에 남아 있는 FAILED Outbox 이벤트 수를 반환한다")
    void countsFailedEvents() {
        // given
        when(outboxEventRepository.countByEventStatus(
                OutboxEventStatus.FAILED
        )).thenReturn(5L);

        // when
        long result =
                outboxPublishService.countFailedEvents();

        // then
        assertThat(result).isEqualTo(5L);

        verify(outboxEventRepository)
                .countByEventStatus(
                        OutboxEventStatus.FAILED
                );
    }

    @Test
    @DisplayName("가장 오래된 PENDING Outbox 이벤트의 대기시간을 반환한다")
    void getsOldestPendingAge() {
        when(outboxEventRepository.findOldestPendingAgeSeconds())
                .thenReturn(12L);

        long result =
                outboxPublishService.getOldestPendingAgeSeconds();

        assertThat(result).isEqualTo(12L);

        verify(outboxEventRepository)
                .findOldestPendingAgeSeconds();
    }

    @Test
    @DisplayName("FAILED 이벤트를 PENDING으로 전환하면 재처리 요청 성공을 반환한다")
    void requeuesFailedEvent() {
        // given
        UUID eventId = UUID.randomUUID();

        when(outboxEventRepository.requeueFailedEvent(eventId))
                .thenReturn(1);

        // when
        boolean result =
                outboxPublishService.requeueFailedEvent(
                        eventId
                );

        // then
        assertThat(result).isTrue();

        verify(outboxEventRepository)
                .requeueFailedEvent(eventId);
    }

    @Test
    @DisplayName("FAILED 상태가 아닌 이벤트는 재처리 요청 실패를 반환한다")
    void returnsFalseWhenEventCannotBeRequeued() {
        // given
        UUID eventId = UUID.randomUUID();

        /*
         * 이벤트가 없거나 현재 상태가
         * PENDING, PROCESSING, PUBLISHED인 경우를 표현한다.
         */
        when(outboxEventRepository.requeueFailedEvent(eventId))
                .thenReturn(0);

        // when
        boolean result =
                outboxPublishService.requeueFailedEvent(
                        eventId
                );

        // then
        assertThat(result).isFalse();

        verify(outboxEventRepository)
                .requeueFailedEvent(eventId);
    }
}
