package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.HoldingSnapshotRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


// SettlementCommandService가 revenue/holdings 조회 Feign 호출까지 끝낸 뒤 마지막에만 이 클래스를 호출해서,
// 트랜잭션(=DB 커넥션 점유 구간)이 holdings 페이징 100회 왕복과 겹치지 않고 최대한 짧게 끝나도록 분리
@Component
@RequiredArgsConstructor
@Slf4j
class SettlementBatchWriter {

    private final SettlementBatchRepository settlementBatchRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    // ⚠️ 임시 진단용: 원래 5였으나(wallet의 단일행 기준을 그대로 가져온 값), 부하테스트에서
    // 실제 소요시간을 모른 채로는 적정값을 정할 수 없어 60으로 넉넉히 풀어 실측 중이다.
    // 실측 후 (batch_size 적용 + 필요시 reWriteBatchedInserts=true 등을 반영한) 근거 있는 값으로 되돌릴 것.
    @Transactional(timeout = 60)
    public void persist(SettlementBatch batch, HoldingSnapshot snapshot, List<DividendPayout> payouts) {
        log.info("[진단]persist 진입 — 커넥션 획득 완료 (batchId={})", batch.getId());
        saveNewBatch(batch);
        holdingSnapshotRepository.save(snapshot);
        dividendPayoutRepository.saveAll(payouts);
        log.info("[진단]persist 완료 — 저장 종료 (batchId={}, payoutCount={})", batch.getId(), payouts.size());
    }

    private void saveNewBatch(SettlementBatch batch) {
        try {
            settlementBatchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            String constraintName = extractConstraintName(e);
            if ("uk_settlement_batches_revenue_id".equals(constraintName)) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE);
            }
            if ("uk_settlement_batches_asset_in_progress".equals(constraintName)) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);
            }
            throw e;
        }
    }

    private String extractConstraintName(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException constraintViolation
                ? constraintViolation.getConstraintName()
                : null;
    }
}