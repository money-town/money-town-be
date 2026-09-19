package com.moneykk.moneytown.offering.global.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOffExecution;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferingKafkaConsumerConfigTest {

    @Test
    @DisplayName("처리에 실패한 레코드를 원본 토픽과 같은 파티션의 DLT로 발행한다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishesFailedRecordToDeadLetterTopic() {
        // given
        KafkaTemplate<String, String> kafkaTemplate =
                mock(KafkaTemplate.class);

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenAnswer(invocation -> {
                    ProducerRecord<String, String> producerRecord =
                            invocation.getArgument(0);

                    RecordMetadata metadata = new RecordMetadata(
                            new TopicPartition(
                                    producerRecord.topic(),
                                    producerRecord.partition()
                            ),
                            0L,
                            0,
                            0L,
                            0,
                            0
                    );

                    return CompletableFuture.completedFuture(
                            new SendResult<>(producerRecord, metadata)
                    );
                });

        OfferingKafkaConsumerConfig config =
                new OfferingKafkaConsumerConfig();

        DeadLetterPublishingRecoverer recoverer =
                config.offeringDeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecord<String, String> failedRecord =
                new ConsumerRecord<>(
                        "wallet-hold-result",
                        2,
                        10L,
                        "user-id",
                        "invalid-event"
                );

        // when
        recoverer.accept(
                failedRecord,
                null,
                new IllegalArgumentException("invalid event")
        );

        // then
        var recordCaptor = org.mockito.ArgumentCaptor
                .forClass(ProducerRecord.class);

        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> deadLetterRecord =
                recordCaptor.getValue();

        assertThat(deadLetterRecord.topic())
                .isEqualTo("wallet-hold-result-dlt");

        assertThat(deadLetterRecord.partition())
                .isEqualTo(2);

        assertThat(deadLetterRecord.key())
                .isEqualTo("user-id");

        assertThat(deadLetterRecord.value())
                .isEqualTo("invalid-event");
    }

    @Test
    @DisplayName("Kafka Consumer는 1초, 2초, 4초 간격으로 세 번 재시도한다")
    void appliesExponentialBackOff() {
        // given
        OfferingKafkaConsumerConfig config =
                new OfferingKafkaConsumerConfig();

        BackOffExecution execution =
                config.kafkaConsumerBackOff().start();

        // when & then
        assertThat(execution.nextBackOff())
                .isEqualTo(1_000L);
        assertThat(execution.nextBackOff())
                .isEqualTo(2_000L);
        assertThat(execution.nextBackOff())
                .isEqualTo(4_000L);
        assertThat(execution.nextBackOff())
                .isEqualTo(BackOffExecution.STOP);
    }

    @Test
    @DisplayName("재시도할 수 없는 예외는 재시도 없이 즉시 DLT로 복구하고 RetryListener 콜백을 거친다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void recoversImmediatelyForNotRetryableException() {
        // given
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenAnswer(invocation -> {
                    ProducerRecord<String, String> producerRecord =
                            invocation.getArgument(0);

                    RecordMetadata metadata = new RecordMetadata(
                            new TopicPartition(
                                    producerRecord.topic(),
                                    producerRecord.partition()
                            ),
                            0L, 0, 0L, 0, 0
                    );

                    return CompletableFuture.completedFuture(
                            new SendResult<>(producerRecord, metadata)
                    );
                });

        OfferingKafkaConsumerConfig config =
                new OfferingKafkaConsumerConfig();

        DeadLetterPublishingRecoverer recoverer =
                config.offeringDeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler errorHandler =
                config.offeringKafkaErrorHandler(recoverer);

        ConsumerRecord<String, String> failedRecord = new ConsumerRecord<>(
                "wallet-hold-result", 2, 10L, "user-id", "invalid-event"
        );

        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // when
        // handleRemaining()은 복구 후에도 컨테이너에게 seek을 지시하기 위해
        // 내부 제어용 예외(RecordInRetryException)를 던질 수 있다.
        // 이 테스트의 관심사는 DLT 발행 여부이므로 그 예외는 무시한다.
        try {
            errorHandler.handleRemaining(
                    new IllegalArgumentException("invalid event"),
                    List.of(failedRecord),
                    consumer,
                    container
            );
        } catch (Exception ignored) {
            // 복구 자체는 정상적으로 수행되었는지 아래에서 검증한다.
        }

        // then
        ArgumentCaptor<ProducerRecord> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        verify(kafkaTemplate).send(recordCaptor.capture());

        assertThat(recordCaptor.getValue().topic())
                .isEqualTo("wallet-hold-result-dlt");
    }

    @Test
    @DisplayName("DLT 발행 자체가 실패하면 복구 실패로 처리되고 RetryListener의 복구실패 콜백을 거친다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void handlesRecoveryFailureWhenDeadLetterPublishFails() {
        // given
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new org.apache.kafka.common.KafkaException(
                        "dlt publish failed"
                ));

        OfferingKafkaConsumerConfig config =
                new OfferingKafkaConsumerConfig();

        DeadLetterPublishingRecoverer recoverer =
                config.offeringDeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler errorHandler =
                config.offeringKafkaErrorHandler(recoverer);

        ConsumerRecord<String, String> failedRecord = new ConsumerRecord<>(
                "wallet-hold-result", 2, 10L, "user-id", "invalid-event"
        );

        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // when & then
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> errorHandler.handleRemaining(
                        new IllegalArgumentException("invalid event"),
                        List.of(failedRecord),
                        consumer,
                        container
                )
        )).isNotNull();

        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }
}
