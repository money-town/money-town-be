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
}
