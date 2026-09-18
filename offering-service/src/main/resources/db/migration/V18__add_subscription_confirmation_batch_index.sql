-- 확정 대기 중인 HOLD_SUCCEEDED 청약을 공모별로 제한 조회한다.
--
-- offering_id로 대상 공모를 제한하고 subscription_id 순서로
-- 최대 N건을 조회하여 별도 정렬과 전체 청약 스캔을 줄인다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_confirmation_batch
    ON p_subscriptions (
        offering_id,
        subscription_id
    )
    WHERE quantity_reserved = TRUE
      AND is_deleted = FALSE
      AND subscription_status = 'HOLD_SUCCEEDED';