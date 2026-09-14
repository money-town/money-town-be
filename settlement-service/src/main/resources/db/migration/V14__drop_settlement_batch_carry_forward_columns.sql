-- 최대 잔여법(Largest Remainder Method) 도입으로 회차마다 잔여금이 남지 않게 되어(remainder=0 불변),
-- 회차 간 잔여금 이월 개념 자체가 사라졌다. 관련 컬럼은 이월 로직과 함께 제거한다.
ALTER TABLE p_settlement_batches
    DROP COLUMN carried_in_amount,
    DROP COLUMN remainder_amount,
    DROP COLUMN carried_out_to_batch_id;