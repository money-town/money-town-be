-- 발행 대기(PENDING) 이벤트 폴링용 부분 인덱스. 폴링 쿼리(ORDER BY created_at, event_id LIMIT n FOR UPDATE SKIP LOCKED)가 전체 스캔하지 않게 한다.
CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON p_outbox_events (created_at, event_id)
    WHERE event_status = 'PENDING';