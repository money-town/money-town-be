-- 청약 타임아웃(reason=RESERVATION_EXPIRED) 보상 요청 시점에 HOLD가 없던 청약을 기록하는 tombstone.
-- Kafka는 토픽 간 순서를 보장하지 않아 SubscriptionReserved가 보상 요청보다 늦게 도착할 수 있는데,
-- 이 기록이 있으면 뒤늦게 도착한 SubscriptionReserved가 HOLD를 생성하지 않도록 막는다.
-- p_wallet_holds와 별도 테이블로 분리 — 해당 테이블은 돈이 실제로 움직인 상태(HELD/RELEASED/COMMITTED/REFUNDED)만
-- 표현하도록 유지하고, "HOLD 자체가 없었던" 케이스를 상태값으로 섞지 않기 위함.
CREATE TABLE p_wallet_expired_reservations (
    subscription_id UUID PRIMARY KEY,
    reason VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);
