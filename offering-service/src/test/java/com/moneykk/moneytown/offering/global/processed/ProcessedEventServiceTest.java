package com.moneykk.moneytown.offering.global.processed;

import com.moneykk.moneytown.common.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {

    private static final String CONSUMER_GROUP = "offering-service";

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventService processedEventService;

    private EventEnvelope<String> envelope() {
        return EventEnvelope.of(
                "SubscriptionConfirmed",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                "correlation-id",
                "payload"
        );
    }

    @Test
    @DisplayName("신규 이벤트면 업무 처리를 실행하고 처리 완료를 기록한다")
    void processesNewEventOnce() {
        // given
        EventEnvelope<String> envelope = envelope();

        when(processedEventRepository.insertIfAbsent(
                envelope.eventId(),
                CONSUMER_GROUP,
                envelope.eventType(),
                UUID.fromString(envelope.aggregateId())
        )).thenReturn(1);

        when(processedEventRepository.markCompleted(
                envelope.eventId(),
                CONSUMER_GROUP
        )).thenReturn(1);

        Runnable businessAction = mock(Runnable.class);

        // when
        boolean processed = processedEventService.processOnce(
                envelope, CONSUMER_GROUP, businessAction
        );

        // then
        assertThat(processed).isTrue();
        verify(businessAction).run();
        verify(processedEventRepository).markCompleted(
                envelope.eventId(), CONSUMER_GROUP
        );
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 업무 처리를 실행하지 않는다")
    void skipsAlreadyProcessedEvent() {
        // given
        EventEnvelope<String> envelope = envelope();

        when(processedEventRepository.insertIfAbsent(
                envelope.eventId(),
                CONSUMER_GROUP,
                envelope.eventType(),
                UUID.fromString(envelope.aggregateId())
        )).thenReturn(0);

        Runnable businessAction = mock(Runnable.class);

        // when
        boolean processed = processedEventService.processOnce(
                envelope, CONSUMER_GROUP, businessAction
        );

        // then
        assertThat(processed).isFalse();
        verify(businessAction, never()).run();
        verify(processedEventRepository, never())
                .markCompleted(envelope.eventId(), CONSUMER_GROUP);
    }

    @Test
    @DisplayName("처리 완료 기록에 실패하면 예외가 발생한다")
    void throwsWhenMarkCompletedFails() {
        // given
        EventEnvelope<String> envelope = envelope();

        when(processedEventRepository.insertIfAbsent(
                envelope.eventId(),
                CONSUMER_GROUP,
                envelope.eventType(),
                UUID.fromString(envelope.aggregateId())
        )).thenReturn(1);

        when(processedEventRepository.markCompleted(
                envelope.eventId(), CONSUMER_GROUP
        )).thenReturn(0);

        Runnable businessAction = mock(Runnable.class);

        // when & then
        assertThatThrownBy(() -> processedEventService.processOnce(
                envelope, CONSUMER_GROUP, businessAction
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("aggregateId가 UUID 형식이 아니면 예외가 발생한다")
    void rejectsInvalidAggregateId() {
        // given
        EventEnvelope<String> envelope = EventEnvelope.of(
                "SubscriptionConfirmed",
                "not-a-uuid",
                UUID.randomUUID(),
                "correlation-id",
                "payload"
        );

        Runnable businessAction = mock(Runnable.class);

        // when & then
        assertThatThrownBy(() -> processedEventService.processOnce(
                envelope, CONSUMER_GROUP, businessAction
        )).isInstanceOf(IllegalArgumentException.class);

        verify(businessAction, never()).run();
    }

    @Test
    @DisplayName("consumerGroup이 비어 있으면 예외가 발생한다")
    void rejectsBlankConsumerGroup() {
        // given
        EventEnvelope<String> envelope = envelope();
        Runnable businessAction = mock(Runnable.class);

        // when & then
        assertThatThrownBy(() -> processedEventService.processOnce(
                envelope, " ", businessAction
        )).isInstanceOf(IllegalArgumentException.class);

        verify(businessAction, never()).run();
    }

    @Test
    @DisplayName("envelope이 없으면 예외가 발생한다")
    void rejectsNullEnvelope() {
        // when & then
        assertThatThrownBy(() -> processedEventService.processOnce(
                null, CONSUMER_GROUP, mock(Runnable.class)
        )).isInstanceOf(NullPointerException.class);
    }
}
