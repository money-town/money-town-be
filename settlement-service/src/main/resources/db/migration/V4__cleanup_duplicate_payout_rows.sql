-- V3에서 만든 UNIQUE 제약을 V5에서 다시 만들기 위한 선행 작업. 부분 실패 후 재실행해도 안전하도록 작성.

-- 1) 기존 제약을 제거 (이미 없다면 조용히 건너뜀 — 재실행 안전)
ALTER TABLE p_dividend_payouts
    DROP CONSTRAINT IF EXISTS uk_dividend_payouts_batch_investor;

ALTER TABLE p_final_settlement_payouts
    DROP CONSTRAINT IF EXISTS uk_final_settlement_payouts_batch_investor;

-- 2) 중복 행 정리: (batch, investor) 조합별로 가장 먼저 생성된 행만 남기고 나머지 삭제.
-- created_at이 같은 행이 있어도 PK를 2차 정렬 기준으로 써서 rn=1이 항상 유일하게 정해진다.
-- 매번 다시 계산하는 방식이라 이미 정리된 상태에서 재실행해도 삭제 대상이 없어 안전하다.
DELETE FROM p_dividend_payouts
WHERE dividend_payout_id IN (
    SELECT dividend_payout_id
    FROM (
        SELECT dividend_payout_id,
               ROW_NUMBER() OVER (
                   PARTITION BY settlement_batch_id, investor_id
                   ORDER BY created_at, dividend_payout_id
               ) AS rn
        FROM p_dividend_payouts
    ) ranked
    WHERE ranked.rn > 1
);

DELETE FROM p_final_settlement_payouts
WHERE final_settlement_payout_id IN (
    SELECT final_settlement_payout_id
    FROM (
        SELECT final_settlement_payout_id,
               ROW_NUMBER() OVER (
                   PARTITION BY final_settlement_batch_id, investor_id
                   ORDER BY created_at, final_settlement_payout_id
               ) AS rn
        FROM p_final_settlement_payouts
    ) ranked
    WHERE ranked.rn > 1
);