-- Holding 배정 결과 장기 대기 청약 조회
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_stuck_holding_allocation
    ON p_subscriptions (updated_at, subscription_id)
    WHERE subscription_status = 'CONFIRMED'
    AND holding_allocation_status = 'PENDING'
    AND is_deleted = FALSE;

-- MANUAL_REVIEW 운영 대상 조회
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscriptions_manual_review
    ON p_subscriptions (subscription_id)
    WHERE subscription_status = 'MANUAL_REVIEW'
    AND is_deleted = FALSE;

-- Wallet 또는 Holding 보상 장기 미완료 조회
CREATE INDEX CONCURRENTLY IF NOT EXISTS
    idx_subscription_compensations_stuck
    ON p_subscription_compensations (updated_at, subscription_id)
    WHERE wallet_status <> 'SUCCEEDED'
    OR holding_status <> 'SUCCEEDED';