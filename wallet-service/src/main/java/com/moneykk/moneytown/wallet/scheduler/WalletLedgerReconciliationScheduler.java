package com.moneykk.moneytown.wallet.scheduler;

import com.moneykk.moneytown.wallet.service.WalletBalanceMismatch;
import com.moneykk.moneytown.wallet.service.WalletLedgerReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLedgerReconciliationScheduler {

    private final WalletLedgerReconciliationService walletLedgerReconciliationService;

    // 매일 새벽 4시(한국시간) 원장-지갑 잔액 1원 단위 정합성 배치 검증
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void reconcile() {
        List<WalletBalanceMismatch> mismatches = walletLedgerReconciliationService.findMismatches();
        if (mismatches.isEmpty()) {
            log.info("원장-지갑 잔액 정합성 검증 완료 - 불일치 없음");
            return;
        }
        log.error("원장-지갑 잔액 불일치 {}건 발견: {}", mismatches.size(), mismatches);
    }
}
