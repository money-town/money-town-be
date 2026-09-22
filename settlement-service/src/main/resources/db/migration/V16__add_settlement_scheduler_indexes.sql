-- DisbursementRetryScheduler가 3~5분마다 도는 status 기반 조회 쿼리들이 인덱스 없이 매번
-- 전체 테이블을 훑는 문제를 해소한다.
-- partial 인덱스로 결정했다(정산 완료 후 payout 대부분은 PAID로 영구
-- 누적되고 PROCESSING/QUEUED/RETRYING은 항상 극소수 — 범용 인덱스는 PAID 행까지 다 담아 계속 커짐).
--
-- p_final_settlement_batches용 인덱스는 의도적으로 제외: 자산당 1행이라 테이블 자체가 영원히
-- 수십~수백 페이지 수준으로 작음. 나중에 이 테이블이 실제로 커지면 그때 추가

-- stalled reclaim (DividendPayoutWriter/FinalSettlementPayoutWriter, status=PROCESSING 고정, 5분 임계값)
CREATE INDEX IF NOT EXISTS idx_dividend_payouts_processing
    ON p_dividend_payouts (updated_at)
    WHERE status = 'PROCESSING' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_final_settlement_payouts_processing
    ON p_final_settlement_payouts (updated_at)
    WHERE status = 'PROCESSING' AND is_deleted = false;

-- DisbursementRetryScheduler의 IN(QUEUED,RETRYING) → DISTINCT batch_id 조회를 인덱스만으로 처리
CREATE INDEX IF NOT EXISTS idx_dividend_payouts_retry_batch
    ON p_dividend_payouts (settlement_batch_id)
    WHERE status IN ('QUEUED', 'RETRYING') AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_final_settlement_payouts_retry_batch
    ON p_final_settlement_payouts (final_settlement_batch_id)
    WHERE status IN ('QUEUED', 'RETRYING') AND is_deleted = false;