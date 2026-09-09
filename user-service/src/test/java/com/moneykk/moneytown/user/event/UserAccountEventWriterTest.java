package com.moneykk.moneytown.user.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.user.event.dto.UserRegisteredPayload;
import com.moneykk.moneytown.user.event.dto.UserWithdrawnPayload;
import com.moneykk.moneytown.user.global.outbox.OutboxEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserAccountEventWriterTest {

    @Mock
    private OutboxEventStore outboxEventStore;

    @InjectMocks
    private UserAccountEventWriter userAccountEventWriter;

    @Test
    @DisplayName("회원가입 이벤트를 공통 EventEnvelope 형식으로 기록한다")
    void recordRegisteredEvent() {
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        ArgumentCaptor<EventEnvelope<?>> captor = envelopeCaptor();

        userAccountEventWriter.recordRegistered(userId, correlationId);

        then(outboxEventStore).should().save(
                org.mockito.ArgumentMatchers.eq(
                        UserAccountEventConstants.AGGREGATE_TYPE
                ),
                org.mockito.ArgumentMatchers.eq(
                        UserAccountEventConstants.TOPIC
                ),
                captor.capture()
        );

        EventEnvelope<?> event = captor.getValue();
        assertThat(event.eventType())
                .isEqualTo(UserAccountEventConstants.USER_REGISTERED);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.aggregateId()).isEqualTo(userId.toString());
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.payload()).isInstanceOf(UserRegisteredPayload.class);
    }

    @Test
    @DisplayName("회원 탈퇴 이벤트에 탈퇴 처리자를 포함한다")
    void recordWithdrawnEvent() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ArgumentCaptor<EventEnvelope<?>> captor = envelopeCaptor();

        userAccountEventWriter.recordWithdrawn(userId, adminId, null);

        then(outboxEventStore).should(times(1)).save(
                org.mockito.ArgumentMatchers.eq(
                        UserAccountEventConstants.AGGREGATE_TYPE
                ),
                org.mockito.ArgumentMatchers.eq(
                        UserAccountEventConstants.TOPIC
                ),
                captor.capture()
        );

        EventEnvelope<?> event = captor.getValue();
        assertThat(event.eventType())
                .isEqualTo(UserAccountEventConstants.USER_WITHDRAWN);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.correlationId()).isNotBlank();
        assertThat(event.payload())
                .isEqualTo(new UserWithdrawnPayload(adminId));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(EventEnvelope.class);
    }
}
