ALTER TABLE p_subscriptions
    ADD COLUMN holding_allocation_status VARCHAR(20),
    ADD COLUMN holding_allocation_error_code VARCHAR(100);

ALTER TABLE p_subscriptions
    ADD CONSTRAINT chk_subscriptions_holding_allocation_status
        CHECK (
            holding_allocation_status IN (
                                          'PENDING',
                                          'SUCCEEDED',
                                          'FAILED'
                )
            );

ALTER TABLE p_subscriptions
    ADD CONSTRAINT chk_subscriptions_holding_allocation_state
        CHECK (
            (
                holding_allocation_status IS NULL
                    AND holding_allocation_error_code IS NULL
                )
                OR (
                holding_allocation_status = 'FAILED'
                    AND holding_allocation_error_code IS NOT NULL
                )
                OR (
                holding_allocation_status IN (
                                              'PENDING',
                                              'SUCCEEDED'
                    )
                    AND holding_allocation_error_code IS NULL
                )
            );