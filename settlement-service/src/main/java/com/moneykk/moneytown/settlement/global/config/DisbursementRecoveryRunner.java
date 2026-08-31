package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisbursementRecoveryRunner implements ApplicationRunner {

    private static final List<PayoutStatus> RESUMABLE_STATUSES = List.of(PayoutStatus.QUEUED, PayoutStatus.RETRYING);

    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendDisbursementService dividendDisbursementService;

    @Override
    public void run(ApplicationArguments args) {
        List<UUID> settlementBatchIds = dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(RESUMABLE_STATUSES);
        if (settlementBatchIds.isEmpty()) {
            return;
        }
        log.info("재개할 정산 회차 {}건을 발견해 지급 워커를 다시 트리거합니다: {}", settlementBatchIds.size(), settlementBatchIds);
        settlementBatchIds.forEach(dividendDisbursementService::disburseAsync);
    }
}