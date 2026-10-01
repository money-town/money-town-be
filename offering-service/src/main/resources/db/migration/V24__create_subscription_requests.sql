CREATE TABLE p_subscription_requests (
    subscription_request_id UUID PRIMARY KEY,
    offering_id UUID NOT NULL,
    user_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    request_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    subscription_id UUID,
    failure_code VARCHAR(100),
    processing_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_subscription_requests_user_key
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_subscription_requests_offering
        FOREIGN KEY (offering_id)
            REFERENCES p_offerings (offering_id),
    CONSTRAINT fk_subscription_requests_subscription
        FOREIGN KEY (subscription_id)
            REFERENCES p_subscriptions (subscription_id),
    CONSTRAINT chk_subscription_requests_quantity
        CHECK (quantity > 0),
    CONSTRAINT chk_subscription_requests_status
        CHECK (request_status IN (
            'QUEUED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED'
        )),
    CONSTRAINT chk_subscription_requests_terminal_result
        CHECK (
            (request_status = 'COMPLETED'
                AND subscription_id IS NOT NULL
                AND failure_code IS NULL)
            OR
            (request_status IN ('REJECTED', 'FAILED')
                AND subscription_id IS NULL
                AND failure_code IS NOT NULL)
            OR
            (request_status IN ('QUEUED', 'PROCESSING')
                AND subscription_id IS NULL
                AND failure_code IS NULL)
        ),
    CONSTRAINT chk_subscription_requests_processing_time
        CHECK (
            (request_status = 'PROCESSING'
                AND processing_started_at IS NOT NULL)
            OR
            (request_status <> 'PROCESSING'
                AND processing_started_at IS NULL)
        ),
    CONSTRAINT chk_subscription_requests_completed_time
        CHECK (
            (request_status IN ('COMPLETED', 'REJECTED', 'FAILED')
                AND completed_at IS NOT NULL)
            OR
            (request_status IN ('QUEUED', 'PROCESSING')
                AND completed_at IS NULL)
        ),
    CONSTRAINT chk_subscription_requests_request_hash
        CHECK (char_length(request_hash) = 64)
);

CREATE INDEX idx_subscription_requests_recovery
    ON p_subscription_requests (processing_started_at, subscription_request_id)
    WHERE request_status = 'PROCESSING';

CREATE INDEX idx_subscription_requests_status_created
    ON p_subscription_requests (request_status, created_at);

COMMENT ON TABLE p_subscription_requests IS
    'HTTP 청약 접수와 비동기 실제 처리 사이의 내구성 있는 작업 상태';

COMMENT ON COLUMN p_subscription_requests.request_hash IS
    '동일 Idempotency-Key에 다른 공모 또는 수량을 사용한 충돌 요청 검증값';

COMMENT ON COLUMN p_subscription_requests.processing_started_at IS
    'Consumer가 처리를 시작한 시각. 처리 중 장애 복구 기준';
