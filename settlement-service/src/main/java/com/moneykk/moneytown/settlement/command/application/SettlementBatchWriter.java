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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


// SettlementCommandService가 revenue/holdings 조회 Feign 호출까지 끝낸 뒤 마지막에만 이 클래스를 호출해서,
// 트랜잭션(=DB 커넥션 점유 구간)이 holdings 페이징 100회 왕복과 겹치지 않고 최대한 짧게 끝나도록 분리
@Component
@RequiredArgsConstructor
class SettlementBatchWriter {

    private final SettlementBatchRepository settlementBatchRepository;
    private final HoldingSnapshotRepository holdingSnapshotRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    // 커넥션 획득 이후 실제 처리가 예상 못 하게 늘어지는 경우에도 오래 붙잡지 않게 한다.
    @Transactional(timeout = 5)
    public void persist(SettlementBatch batch, HoldingSnapshot snapshot, List<DividendPayout> payouts) {
        saveNewBatch(batch);
        holdingSnapshotRepository.save(snapshot);
        dividendPayoutRepository.saveAll(payouts);
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