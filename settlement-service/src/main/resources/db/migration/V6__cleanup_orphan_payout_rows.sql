-- V7에서 FK를 NOT VALID로 추가하기 전에 고아 행을 미리 정리
DELETE FROM p_holdings_snapshots
WHERE settlement_batch_id NOT IN (SELECT settlement_batch_id FROM p_settlement_batches);

DELETE FROM p_dividend_payouts
WHERE settlement_batch_id NOT IN (SELECT settlement_batch_id FROM p_settlement_batches);

DELETE FROM p_final_settlement_payouts
WHERE final_settlement_batch_id NOT IN (SELECT final_settlement_batch_id FROM p_final_settlement_batches);