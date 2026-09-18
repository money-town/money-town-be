-- 매진 공모의 Wallet HOLD 결과 처리 시 아직 완료되지 않은 청약을 빠르게 찾는다.
-- HOLD_SUCCEEDED와 CONFIRMED는 일괄 확정 준비가 끝난 상태이므로 인덱스에서 제외한다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_reserved_pending_hold
    ON p_subscriptions (offering_id)
    WHERE quantity_reserved = TRUE
      AND is_deleted = FALSE
      AND subscription_status NOT IN (
          'HOLD_SUCCEEDED',
          'CONFIRMED'
      );
