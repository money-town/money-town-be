-- FK를 NOT VALID로 추가: 기존 행 검증을 건너뛰므로 ACCESS EXCLUSIVE 잠금이 메타데이터 변경만큼만 짧게 걸린다.
-- 검증(VALIDATE CONSTRAINT)은 V7에서 별도 트랜잭션으로 수행한다 — 같은 트랜잭션에서 하면
-- 여기서 잡은 잠금이 검증이 끝날 때까지 유지되어 잠금 없이 추가한 의미가 없어지기 때문.
ALTER TABLE p_holdings_snapshots
    ADD CONSTRAINT fk_holdings_snapshots_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id) NOT VALID;

ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT fk_dividend_payouts_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id) NOT VALID;

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT fk_final_settlement_payouts_final_settlement_batch
    FOREIGN KEY (final_settlement_batch_id) REFERENCES p_final_settlement_batches (final_settlement_batch_id) NOT VALID;

-- 고아 행 정리: VALIDATE CONSTRAINT가 실패하지 않도록 검증 전에 미리 제거한다.
DELETE FROM p_holdings_snapshots
WHERE settlement_batch_id NOT IN (SELECT settlement_batch_id FROM p_settlement_batches);

DELETE FROM p_dividend_payouts
WHERE settlement_batch_id NOT IN (SELECT settlement_batch_id FROM p_settlement_batches);

DELETE FROM p_final_settlement_payouts
WHERE final_settlement_batch_id NOT IN (SELECT final_settlement_batch_id FROM p_final_settlement_batches);