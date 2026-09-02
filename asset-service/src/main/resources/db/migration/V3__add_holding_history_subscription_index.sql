CREATE INDEX idx_holding_histories_subscription_created_at
    ON p_holding_histories (subscription_id, created_at);
