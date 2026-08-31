-- 재처리/재전달로 동일 (batch, investor) 조합의 payout row가 중복 생성되는 것을 DB 레벨에서 방지.

ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT uk_dividend_payouts_batch_investor UNIQUE (settlement_batch_id, investor_id);

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT uk_final_settlement_payouts_batch_investor UNIQUE (final_settlement_batch_id, investor_id);