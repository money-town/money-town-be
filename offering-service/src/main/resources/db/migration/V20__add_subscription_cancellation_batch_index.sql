CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_cancellation_batch
ON p_subscriptions (
offering_id,
subscription_id
)
WHERE is_deleted = FALSE
  AND subscription_status IN (
        'PROCESSING',
        'HOLD_SUCCEEDED',
        'CONFIRMED'
);