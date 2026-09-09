package com.moneykk.moneytown.asset.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingAllocationSucceededPayload;
import com.moneykk.moneytown.asset.infrastructure.kafka.event.HoldingRevocationSucceededPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private HoldingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new HoldingEventPublisher(kafkaTemplate, objectMapper);

        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    @DisplayName("지분 배정 성공 결과를 allocation 결과 토픽에 발행한다")
    void publishesAllocationSuccess() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID holdingId = UUID.randomUUID();

        publisher.publishAllocationSucceeded(
                subscriptionId,
                userId,
                "correlation-1",
                new HoldingAllocationSucceededPayload(
                        assetId,
                        holdingId,
                        100L,
                        "ALLOCATED"
                )
        );

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq("holding-allocation-result"),
                org.mockito.ArgumentMatchers.eq(subscriptionId.toString()),
                messageCaptor.capture()
        );

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.get("eventType").asText()).isEqualTo("HoldingAllocationSucceeded");
        assertThat(message.get("aggregateId").asText()).isEqualTo(subscriptionId.toString());
        assertThat(message.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(message.get("payload").get("assetId").asText()).isEqualTo(assetId.toString());
        assertThat(message.get("payload").get("result").asText()).isEqualTo("ALLOCATED");
    }

    @Test
    @DisplayName("지분 회수 성공 결과를 revocation 결과 토픽에 발행한다")
    void publishesRevocationSuccess() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        publisher.publishRevocationSucceeded(
                subscriptionId,
                userId,
                "correlation-2",
                new HoldingRevocationSucceededPayload(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        100L,
                        "REVOKED",
                        null
                )
        );

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq("holding-revocation-result"),
                org.mockito.ArgumentMatchers.eq(subscriptionId.toString()),
                messageCaptor.capture()
        );

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.get("eventType").asText()).isEqualTo("HoldingRevocationSucceeded");
        assertThat(message.get("aggregateId").asText()).isEqualTo(subscriptionId.toString());
        assertThat(message.get("payload").get("result").asText()).isEqualTo("REVOKED");
    }
}
