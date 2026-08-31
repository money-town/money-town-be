-- V3에서 만든 UNIQUE 제약을 V5에서 CONCURRENTLY 방식으로 다시 만들기 위한 선행 작업.
-- 1) 기존 제약/인덱스를 제거 (일반 ALTER는 검증할 데이터가 없어 즉시 끝남)
ALTER TABLE p_dividend_payouts
    DROP CONSTRAINT uk_dividend_payouts_batch_investor;

ALTER TABLE p_final_settlement_payouts
    DROP CONSTRAINT uk_final_settlement_payouts_batch_investor;

-- 2) 혹시 모를 중복 행 정리: (batch, investor) 조합별로 가장 최근 행만 남김
DELETE FROM p_dividend_payouts d
    USING p_dividend_payouts newer
    WHERE d.settlement_batch_id = newer.settlement_batch_id
      AND d.investor_id = newer.investor_id
      AND d.created_at < newer.created_at;

DELETE FROM p_final_settlement_payouts d
    USING p_final_settlement_payouts newer
    WHERE d.final_settlement_batch_id = newer.final_settlement_batch_id
      AND d.investor_id = newer.investor_id
      AND d.created_at < newer.created_at;