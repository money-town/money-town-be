CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscription_compensations_recovery
    ON p_subscription_compensations (
    updated_at ASC,
    subscription_id ASC
    )
    WHERE wallet_status <> 'SUCCEEDED'
    OR holding_status <> 'SUCCEEDED';