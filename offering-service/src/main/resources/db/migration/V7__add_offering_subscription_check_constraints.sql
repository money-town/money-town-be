-- 공모 상태
ALTER TABLE p_offerings
    ADD CONSTRAINT chk_offerings_status
        CHECK (
            offering_status IN (
                                'DRAFT',
                                'REVIEW_REQUESTED',
                                'SCHEDULED',
                                'OPEN',
                                'SOLD_OUT',
                                'CLOSED',
                                'REJECTED',
                                'CANCELLING',
                                'CANCELLED'
                )
            );

-- 공모 취소 유형. 취소 전에는 NULL 허용
ALTER TABLE p_offerings
    ADD CONSTRAINT chk_offerings_cancellation_type
        CHECK (
            cancellation_type IN (
                                  'ADMIN_CANCELLED',
                                  'UNDER_SUBSCRIBED'
                )
            );

-- 청약 상태
ALTER TABLE p_subscriptions
    ADD CONSTRAINT chk_subscriptions_status
        CHECK (
            subscription_status IN (
                                    'PROCESSING',
                                    'CONFIRMED',
                                    'COMPENSATING',
                                    'REJECTED',
                                    'CANCELLED',
                                    'MANUAL_REVIEW'
                )
            );

-- 청약 취소 유형. 공모 취소 사유가 없는 경우 NULL 허용
ALTER TABLE p_subscriptions
    ADD CONSTRAINT chk_subscriptions_cancellation_type
        CHECK (
            cancellation_type IN (
                                  'OFFERING_ADMIN_CANCELLED',
                                  'OFFERING_UNDER_SUBSCRIBED'
                )
            );