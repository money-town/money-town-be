CREATE TABLE p_holding_subscription_states (
    subscription_id UUID PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'BLOCKED', 'ALLOCATED', 'REVOKED')),
    holding_id UUID REFERENCES p_holdings(holding_id),
    block_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_holding_subscription_state
        CHECK (
            (status = 'BLOCKED' AND block_reason IS NOT NULL)
            OR (status IN ('ALLOCATED', 'REVOKED') AND holding_id IS NOT NULL AND block_reason IS NULL)
            OR (status = 'PENDING' AND holding_id IS NULL AND block_reason IS NULL)
        )
);

-- 기존 배정·회수 이력을 청약별 현재 상태로 이관함
INSERT INTO p_holding_subscription_states (
    subscription_id,
    status,
    holding_id,
    created_at,
    updated_at
)
SELECT
    subscription_id,
    CASE
        WHEN BOOL_OR(history_type = 'REVOKE') THEN 'REVOKED'
        ELSE 'ALLOCATED'
    END,
    (ARRAY_AGG(holding_id ORDER BY created_at DESC))[1],
    MIN(created_at),
    MAX(created_at)
FROM p_holding_histories
WHERE subscription_id IS NOT NULL
  AND history_type IN ('ALLOCATE', 'REVOKE')
GROUP BY subscription_id;
