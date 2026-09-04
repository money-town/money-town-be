CREATE TABLE p_subscription_compensations (
                                              compensation_id UUID PRIMARY KEY,
                                              subscription_id UUID NOT NULL,

                                              wallet_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                              holding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

                                              wallet_error_code VARCHAR(100),
                                              holding_error_code VARCHAR(100),

                                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              created_by UUID NOT NULL,
                                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_by UUID NOT NULL,

                                              CONSTRAINT fk_subscription_compensations_subscription
                                                  FOREIGN KEY (subscription_id)
                                                      REFERENCES p_subscriptions (subscription_id),

                                              CONSTRAINT uq_subscription_compensations_subscription
                                                  UNIQUE (subscription_id),

                                              CONSTRAINT chk_subscription_compensations_wallet_status
                                                  CHECK (
                                                      wallet_status IN (
                                                                        'PENDING',
                                                                        'SUCCEEDED',
                                                                        'FAILED'
                                                          )
                                                      ),

                                              CONSTRAINT chk_subscription_compensations_holding_status
                                                  CHECK (
                                                      holding_status IN (
                                                                         'PENDING',
                                                                         'SUCCEEDED',
                                                                         'FAILED'
                                                          )
                                                      )
);