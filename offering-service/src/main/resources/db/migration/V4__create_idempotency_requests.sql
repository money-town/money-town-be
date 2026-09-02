CREATE TABLE p_idempotency_requests (
                                        idempotency_request_id UUID PRIMARY KEY,

                                        user_id UUID NOT NULL,

                                        operation VARCHAR(50) NOT NULL,

                                        idempotency_key VARCHAR(100) NOT NULL,

                                        request_hash VARCHAR(64) NOT NULL,

                                        resource_type VARCHAR(50) NOT NULL DEFAULT 'SUBSCRIPTION',

                                        resource_id UUID,

                                        idempotency_request_status VARCHAR(20)
                                            NOT NULL DEFAULT 'PROCESSING',

                                        response_code INTEGER,

                                        created_at TIMESTAMPTZ
                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        completed_at TIMESTAMPTZ,

                                        CONSTRAINT uq_idempotency_requests_user_operation_key
                                            UNIQUE (user_id, operation, idempotency_key),

                                        CONSTRAINT fk_idempotency_requests_resource
                                            FOREIGN KEY (resource_id)
                                                REFERENCES p_subscriptions (subscription_id),

                                        CONSTRAINT chk_idempotency_request_status
                                            CHECK (
                                                idempotency_request_status IN (
                                                                               'PROCESSING',
                                                                               'COMPLETED',
                                                                               'FAILED'
                                                    )
                                                )
);