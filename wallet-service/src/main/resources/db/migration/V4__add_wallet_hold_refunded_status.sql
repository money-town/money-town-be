-- COMMITTED 이후 보상(REFUND) 처리 시 종료 상태로 전이시키기 위한 값 추가.
-- 허용값을 넓히기만 하므로 기존 행(HELD/RELEASED/COMMITTED)과 충돌하지 않는다.
ALTER TABLE p_wallet_holds DROP CONSTRAINT p_wallet_holds_status_check;

ALTER TABLE p_wallet_holds ADD CONSTRAINT ck_wallet_holds_status
    CHECK (status IN ('HELD', 'RELEASED', 'COMMITTED', 'REFUNDED'));
