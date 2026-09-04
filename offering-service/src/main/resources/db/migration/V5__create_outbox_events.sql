CREATE TABLE p_outbox_events (
                                 event_id UUID PRIMARY KEY,

                                 aggregate_type VARCHAR(50) NOT NULL,
                                 aggregate_id UUID NOT NULL,

                                 event_type VARCHAR(100) NOT NULL,
                                 topic VARCHAR(100) NOT NULL,

                                 payload JSONB NOT NULL,

                                 event_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                 retry_count INTEGER NOT NULL DEFAULT 0,

                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 processing_started_at TIMESTAMPTZ,
                                 next_retry_at TIMESTAMPTZ,
                                 published_at TIMESTAMPTZ,

                                 last_error TEXT,

                                 CONSTRAINT chk_outbox_events_status
                                     CHECK (
                                         event_status IN (
                                                          'PENDING',
                                                          'PROCESSING',
                                                          'PUBLISHED',
                                                          'FAILED'
                                             )
                                         ),

                                 CONSTRAINT chk_outbox_events_retry_count
                                     CHECK (retry_count >= 0)
);

COMMENT ON COLUMN p_outbox_events.event_id IS
    'Kafka EventEnvelope의 eventId와 동일한 이벤트 ID';

COMMENT ON COLUMN p_outbox_events.aggregate_id IS
    '이벤트 발생 Aggregate의 ID. 물리 FK 없이 논리 참조';

COMMENT ON COLUMN p_outbox_events.payload IS
    '이벤트 생성 시점의 전체 EventEnvelope JSON 스냅샷';

COMMENT ON COLUMN p_outbox_events.created_at IS
    'Outbox에 이벤트를 최초 저장한 시각. 재시도 시에도 변경하지 않음';

COMMENT ON COLUMN p_outbox_events.processing_started_at IS
    '현재 발행 시도 시작 시각. PROCESSING 전환 시마다 갱신, 장시간 처리중 이벤트 복구 판단';

COMMENT ON COLUMN p_outbox_events.next_retry_at IS
    '발행 실패 후 다음 재시도 예정 시각. 최초 발행 대기 시 NULL';

COMMENT ON COLUMN p_outbox_events.published_at IS
    'Kafka 발행 성공 응답을 확인한 시각. PUBLISHED 전환 시 기록';

COMMENT ON COLUMN p_outbox_events.last_error IS
    '마지막 발행 실패 사유';