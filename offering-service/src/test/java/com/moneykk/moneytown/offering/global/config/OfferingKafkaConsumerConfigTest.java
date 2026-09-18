package com.moneykk.moneytown.offering.global.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOffExecution;

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
}
