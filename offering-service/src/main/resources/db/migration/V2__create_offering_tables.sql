-- Offering Service schema baseline.
-- Add future schema changes as V2__, V3__ and later migrations.
CREATE TABLE p_offerings (
                             offering_id UUID PRIMARY KEY,
                             asset_id UUID NOT NULL,
                             issuer_id UUID NOT NULL,

                             title VARCHAR(200) NOT NULL,

                             price_per_unit NUMERIC(19, 2) NOT NULL,
                             total_quantity BIGINT NOT NULL,
                             remaining_quantity BIGINT NOT NULL,

                             min_subscription_quantity BIGINT NOT NULL DEFAULT 1,
                             max_subscription_quantity BIGINT NOT NULL,

                             start_at TIMESTAMPTZ NOT NULL,
                             end_at TIMESTAMPTZ NOT NULL,

                             offering_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                             review_requested_at TIMESTAMPTZ,
                             reviewed_at TIMESTAMPTZ,
                             reviewed_by UUID,
                             rejection_reason VARCHAR(500),

                             cancelled_at TIMESTAMPTZ,
                             cancellation_type VARCHAR(30),

                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             created_by UUID NOT NULL,
                             updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_by UUID NOT NULL,

                             is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                             deleted_at TIMESTAMPTZ,
                             deleted_by UUID,

                             CONSTRAINT chk_offerings_price_per_unit_positive
                                 CHECK (price_per_unit > 0),

                             CONSTRAINT chk_offerings_total_quantity_positive
                                 CHECK (total_quantity > 0),

                             CONSTRAINT chk_offerings_remaining_quantity
                                 CHECK (
                                     remaining_quantity >= 0
                                         AND remaining_quantity <= total_quantity
                                     ),

                             CONSTRAINT chk_offerings_min_subscription_quantity
                                 CHECK (min_subscription_quantity >= 1),

                             CONSTRAINT chk_offerings_max_subscription_quantity
                                 CHECK (
                                     max_subscription_quantity >= min_subscription_quantity
                                         AND max_subscription_quantity <= total_quantity
                                     ),

                             CONSTRAINT chk_offerings_period
                                 CHECK (start_at < end_at)
);