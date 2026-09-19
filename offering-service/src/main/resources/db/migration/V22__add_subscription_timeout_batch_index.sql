CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_timeout_batch
ON p_subscriptions (
    offering_id,
    reservation_expires_at,
    subscription_id
)
WHERE subscription_status = 'PROCESSING'
  AND is_deleted = FALSE;
