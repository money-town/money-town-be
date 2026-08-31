-- 존재하지 않는 정산 회차를 가리키는 스냅샷/지급 행(고아 행) 생성을 DB 레벨에서 방지.

ALTER TABLE p_holdings_snapshots
    ADD CONSTRAINT fk_holdings_snapshots_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id);

ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT fk_dividend_payouts_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id);

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT fk_final_settlement_payouts_final_settlement_batch
    FOREIGN KEY (final_settlement_batch_id) REFERENCES p_final_settlement_batches (final_settlement_batch_id);