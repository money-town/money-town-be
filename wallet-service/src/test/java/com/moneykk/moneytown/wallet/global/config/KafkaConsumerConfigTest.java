package com.moneykk.moneytown.wallet.global.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Test
    @DisplayName("IllegalArgumentException은 재시도 없이 즉시 DLT로 보낸다")
    void nonRetryableException_recoversImmediately() {
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);
        when(kafkaOperations.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        CommonErrorHandler errorHandler = config.kafkaConsumerErrorHandler(kafkaOperations);

        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("subscription-reserved", 0, 5L, "key", "value");
        errorHandler.handleRemaining(
                new IllegalArgumentException("bad aggregateId"), List.of(record), mock(Consumer.class), mock(MessageListenerContainer.class));

        ArgumentCaptor<ProducerRecord> dltRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations).send(dltRecordCaptor.capture());
        assertThat(dltRecordCaptor.getValue().topic()).isEqualTo("subscription-reserved-dlt");
    }

    @Test
    @DisplayName("재시도 가능한 예외는 첫 실패만으로는 DLT로 보내지 않고 재시도 대기 상태로 남긴다")
    void retryableException_doesNotRecoverOnFirstFailure() {
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

        CommonErrorHandler errorHandler = config.kafkaConsumerErrorHandler(kafkaOperations);

        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("subscription-reserved", 0, 5L, "key", "value");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // RecordInRetryException(package-private)은 "아직 재시도 중, 다음 poll에서 같은 레코드가 다시 온다"는
        // Spring Kafka의 정상 신호라 클래스 참조는 못 하고 이름으로만 확인한다.
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> errorHandler.handleRemaining(
                new RuntimeException("transient failure"), List.of(record), consumer, container));
        assertEquals("RecordInRetryException", thrown.getClass().getSimpleName());

        verify(kafkaOperations, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("재시도 가능한 예외도 백오프(1s->2s->4s)를 다 소진하면 DLT로 보낸다")
    void retryableException_recoversAfterBackOffExhausted() {
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);
        when(kafkaOperations.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        CommonErrorHandler errorHandler = config.kafkaConsumerErrorHandler(kafkaOperations);

        // 같은 토픽/파티션/오프셋으로 반복 실패해야 동일 레코드의 재시도로 취급된다.
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("subscription-reserved", 0, 5L, "key", "value");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // 실제 운영 백오프(1s+2s+4s=7s)를 그대로 소진시켜야 recoverer 위임을 신뢰성 있게 검증할 수 있다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> errorHandler.handleRemaining(
                    new RuntimeException("transient failure"), List.of(record), consumer, container));
            assertEquals("RecordInRetryException", thrown.getClass().getSimpleName());
            verify(kafkaOperations, never()).send(any(ProducerRecord.class));
        }

        // 백오프가 완전히 끝났음을 보장하기 위해 마지막 간격(4s)만큼 실제로 대기한다.
        await(4_100L);

        errorHandler.handleRemaining(
                new RuntimeException("transient failure"), List.of(record), consumer, container);

        ArgumentCaptor<ProducerRecord> dltRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations).send(dltRecordCaptor.capture());
        assertThat(dltRecordCaptor.getValue().topic()).isEqualTo("subscription-reserved-dlt");
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
