CREATE TABLE p_processed_events (
                                    processed_event_id UUID PRIMARY KEY,

                                    event_id UUID NOT NULL,
                                    consumer_group VARCHAR(100) NOT NULL,
                                    event_type VARCHAR(100) NOT NULL,

                                    aggregate_id UUID,

                                    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT uq_processed_events_event_consumer_group
                                        UNIQUE (event_id, consumer_group)
);

COMMENT ON COLUMN p_processed_events.event_id IS
    '수신한 EventEnvelope의 eventId. 발행 서비스 이벤트를 논리 참조';

COMMENT ON COLUMN p_processed_events.consumer_group IS
    '이벤트를 처리한 Kafka Consumer Group';

COMMENT ON COLUMN p_processed_events.aggregate_id IS
    '소비 서비스에서 처리한 대상 Aggregate의 ID';

COMMENT ON COLUMN p_processed_events.processed_at IS
    '비즈니스 처리 성공 시 기록하는 처리 완료 시각';