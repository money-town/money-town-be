-- FK를 NOT VALID로 추가: 기존 행 검증을 건너뛰므로 잠금이 메타데이터 변경만큼만 짧게 걸린다.
-- 고아 행은 V6에서 이미 정리했다 — 같은 트랜잭션에 두면 여기서 잡는 SHARE ROW EXCLUSIVE
-- 잠금이 그 정리가 끝날 때까지 유지되어 짧게 잠근 의미가 없어지기 때문에 분리
-- 검증(VALIDATE CONSTRAINT)은 V8에서 별도 트랜잭션으로 수행
ALTER TABLE p_holdings_snapshots
    ADD CONSTRAINT fk_holdings_snapshots_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id) NOT VALID;

ALTER TABLE p_dividend_payouts
    ADD CONSTRAINT fk_dividend_payouts_settlement_batch
    FOREIGN KEY (settlement_batch_id) REFERENCES p_settlement_batches (settlement_batch_id) NOT VALID;

ALTER TABLE p_final_settlement_payouts
    ADD CONSTRAINT fk_final_settlement_payouts_final_settlement_batch
    FOREIGN KEY (final_settlement_batch_id) REFERENCES p_final_settlement_batches (final_settlement_batch_id) NOT VALID;