package com.moneykk.moneytown.analysis.fds.infrastructure.kafka.consumer;



import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.fds.command.application.PostFdsService;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.SubscriptionEventPayload;
import com.moneykk.moneytown.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventConsumer {

    private static final TypeReference<EventEnvelope<SubscriptionEventPayload>> TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final PostFdsService postFdsService;


    @KafkaListener(topics = "${fds.kafka.subscription-topic}")
    public void consumeSubscriptionEvent(String message){
        EventEnvelope<SubscriptionEventPayload> envelope;
        try{
            envelope = objectMapper.readValue(message, TYPE);
        }catch (JsonProcessingException e){
            // TODO: RETRY & DLT
            log.error("subscription 이벤트 역직렬화 실패: {}",message, e);
            return;
        }

        log.info("consume start eventId={} type={}", envelope.eventId(), envelope.eventType());
        try{

            postFdsService.handle(envelope);
            log.info("consume success eventId={}", envelope.eventId());
        }catch (Exception e){
            log.error("post-fds 처리 실패 eventId={}", envelope.eventId(), e);
            // MVP: 로그 후 ack (DLQ/리트라이)
        }
    }
}
