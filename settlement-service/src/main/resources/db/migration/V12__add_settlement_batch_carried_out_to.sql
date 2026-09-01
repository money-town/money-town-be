-- 완료된 배치의 remainderAmount는 다음 회차로 이월된 뒤에도 값이 그대로 남아 있어,
-- 같은 assetId/recordDate의 완료 배치가 여러 개면 이미 이월된 잔여금이 다시 선택될 수
-- 있었다. 이월 대상 배치를 기록해 "아직 이월 안 된" 배치만 후보가 되도록 한다.
ALTER TABLE p_settlement_batches
    ADD COLUMN carried_out_to_batch_id UUID;