-- UNHOLD/REFUND 원장에 보상 사유(reason)를 남기기 위한 컬럼.
-- 기존 거래타입(DEPOSIT/WITHDRAW/HOLD/DEDUCT/DIVIDEND/SETTLEMENT)은 reason이 없어 NULL로 남는다.
-- append-only 테이블이라 컬럼만 추가하고 백필하지 않는다 (과거 행은 원래 이 정보가 없었음).
ALTER TABLE p_wallet_transactions ADD COLUMN reason VARCHAR(50);
