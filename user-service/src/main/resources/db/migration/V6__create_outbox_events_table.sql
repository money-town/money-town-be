CREATE TABLE p_outbox_events
(
    event_id             UUID         NOT NULL,
    aggregate_type       VARCHAR(50)  NOT NULL,
    aggregate_id         UUID         NOT NULL,
    event_type           VARCHAR(100) NOT NULL,
    topic                VARCHAR(100) NOT NULL,
    payload              JSONB        NOT NULL,
    event_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMPTZ NULL,
    next_retry_at        TIMESTAMPTZ  NULL,
    published_at         TIMESTAMPTZ  NULL,
    last_error           TEXT         NULL,

    CONSTRAINT pk_p_outbox_events
        PRIMARY KEY (event_id),

    CONSTRAINT ck_p_outbox_events_status
        CHECK (event_status IN (
            'PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'
        )),

    CONSTRAINT ck_p_outbox_events_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX idx_p_outbox_events_publish
    ON p_outbox_events (event_status, next_retry_at, created_at)
    WHERE event_status = 'PENDING';

CREATE INDEX idx_p_outbox_events_aggregate
    ON p_outbox_events (aggregate_type, aggregate_id, created_at);

COMMENT ON TABLE p_outbox_events
    IS '도메인 변경과 같은 트랜잭션에서 저장하는 Kafka 발행 대기 이벤트';

COMMENT ON COLUMN p_outbox_events.event_id
    IS 'EventEnvelope eventId와 동일한 이벤트 식별자';

COMMENT ON COLUMN p_outbox_events.payload
    IS 'EventEnvelope 전체 JSON';

COMMENT ON COLUMN p_outbox_events.event_status
    IS '발행 상태: PENDING, PROCESSING, PUBLISHED, FAILED';

COMMENT ON COLUMN p_outbox_events.processing_started_at
    IS '발행 선점 시각 및 장시간 처리 이벤트 복구 기준';
