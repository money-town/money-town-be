ALTER TABLE p_subscriptions
DROP CONSTRAINT IF EXISTS chk_subscriptions_status;

ALTER TABLE p_subscriptions
    ADD CONSTRAINT chk_subscriptions_status
        CHECK (
            subscription_status IN (
                                    'PROCESSING',
                                    'HOLD_SUCCEEDED',
                                    'CONFIRMED',
                                    'COMPENSATING',
                                    'REJECTED',
                                    'CANCELLED',
                                    'MANUAL_REVIEW'
                )
            );
