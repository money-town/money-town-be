CREATE TABLE p_subscriptions (
                                 subscription_id UUID PRIMARY KEY,
                                 offering_id UUID NOT NULL,
                                 user_id UUID NOT NULL,

                                 quantity BIGINT NOT NULL,
                                 price_per_unit BIGINT NOT NULL,
                                 amount BIGINT NOT NULL,

                                 subscription_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
                                 quantity_reserved BOOLEAN NOT NULL DEFAULT TRUE,

                                 failure_code VARCHAR(50),

                                 cancelled_at TIMESTAMPTZ,
                                 cancellation_type VARCHAR(50),

                                 reservation_expires_at TIMESTAMPTZ,
                                 confirmed_at TIMESTAMPTZ,

                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 created_by UUID NOT NULL,
                                 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_by UUID NOT NULL,

                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                 deleted_at TIMESTAMPTZ,
                                 deleted_by UUID,

                                 CONSTRAINT uq_subscriptions_offering_user
                                     UNIQUE (offering_id, user_id),

                                 CONSTRAINT fk_subscriptions_offering
                                     FOREIGN KEY (offering_id)
                                         REFERENCES p_offerings (offering_id),

                                 CONSTRAINT chk_subscriptions_quantity_positive
                                     CHECK (quantity > 0),

                                 CONSTRAINT chk_subscriptions_price_per_unit_positive
                                     CHECK (price_per_unit > 0),

                                 CONSTRAINT chk_subscriptions_amount_positive
                                     CHECK (amount > 0)
);