-- 만료된 PROCESSING 청약 조회
CREATE INDEX idx_subscriptions_expired_processing
    ON p_subscriptions (reservation_expires_at, subscription_id)
    WHERE subscription_status = 'PROCESSING'
      AND is_deleted = false;

-- 모집 미달 공모 조회
CREATE INDEX idx_offerings_under_subscribed
    ON p_offerings (end_at, offering_id)
    WHERE offering_status IN ('OPEN', 'SOLD_OUT', 'CLOSED')
      AND remaining_quantity > 0
      AND is_deleted = false;

-- 발행 대기 Outbox 조회
CREATE INDEX idx_outbox_pending_publish
    ON p_outbox_events (created_at, event_id)
    WHERE event_status = 'PENDING';

-- 장시간 PROCESSING Outbox 복구
CREATE INDEX idx_outbox_processing_recovery
    ON p_outbox_events (processing_started_at, event_id)
    WHERE event_status = 'PROCESSING';