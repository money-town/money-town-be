package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatusUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RevenueTransferStatusNotifier {

    private static final String SYSTEM_ROLE = "SYSTEM";

    private final AssetServiceClient assetServiceClient;

    // 폴링 스케줄러 전용 — RevenuePollingScheduler.tryOpenBatch는 forEach로 한 페이지의 여러 수익을 순회하므로,
    // 여기서 예외를 던지면 같은 페이지의 나머지 수익 처리까지 통째로 막힌다.
    // 실패해도 revenue가 READY로 남아 다음 폴링 주기(3분)가 다시 통보를 시도하므로
    // (정산 회차 중복 개시 자체는 이미 revenue_id UNIQUE 제약으로 막혀 있음) 삼켜도 안전하다.
    public void notifyTransferred(UUID revenueId) {
        try {
            notifyTransferredOrThrow(revenueId);
        } catch (Exception e) {
            log.warn("자산 서비스에 수익 전달 완료 통보 실패 (revenueId={})", revenueId, e);
        }
    }

    // Kafka Consumer 전용 — 메시지 1건당 독립 실행이라 예외를 던져도 다른 이벤트 처리를 막지 않음
    // 대신 Kafka 자체 재시도(SettlementKafkaConsumerConfig, 3초 간격 3회 후 DLT)가 걸림
    // 폴링과 달리 실패를 삼키면 Kafka 재시도를 우회하고 3분 폴링 백스톱에만 의존하게 되므로 여기서는 예외 전파
    public void notifyTransferredOrThrow(UUID revenueId) {
        assetServiceClient.updateRevenueTransferStatus(
                SYSTEM_ROLE, revenueId, new RevenueTransferStatusUpdateRequest(RevenueTransferStatus.TRANSFERRED, null));
    }
}