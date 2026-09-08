-- 오래된 PROCESSING 멱등 요청을 생성 순서대로 복구하기 위한 인덱스
CREATE INDEX idx_idempotency_processing_recovery
    ON p_idempotency_requests (
                               created_at,
                               idempotency_request_id
        )
    WHERE idempotency_request_status = 'PROCESSING'
      AND resource_id IS NULL;