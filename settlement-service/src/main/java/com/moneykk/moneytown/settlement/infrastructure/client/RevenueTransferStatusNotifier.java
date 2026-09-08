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

    // asset-service의 revenue.transferStatus 갱신은 MVP에서 생략 가능한 부가 통보이고,
    // 정산 회차 중복 개시는 이미 revenue_id UNIQUE 제약으로 막혀 있어 실패해도 정산 흐름을 막지 않는다.
    public void notifyTransferred(UUID revenueId) {
        try {
            assetServiceClient.updateRevenueTransferStatus(
                    SYSTEM_ROLE, revenueId, new RevenueTransferStatusUpdateRequest(RevenueTransferStatus.TRANSFERRED, null));
        } catch (Exception e) {
            log.warn("자산 서비스에 수익 전달 완료 통보 실패 (revenueId={})", revenueId, e);
        }
    }
}