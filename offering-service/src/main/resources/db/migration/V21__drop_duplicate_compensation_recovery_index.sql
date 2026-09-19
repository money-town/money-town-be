-- V14와 V19에서 동일한 컬럼과 조건의 인덱스가 중복 생성됐다.
--
-- 기존 V14 인덱스인 idx_subscription_compensations_stuck을 유지하고,
-- V19에서 추가된 중복 인덱스만 제거한다.
DROP INDEX CONCURRENTLY IF EXISTS
    idx_subscription_compensations_recovery;