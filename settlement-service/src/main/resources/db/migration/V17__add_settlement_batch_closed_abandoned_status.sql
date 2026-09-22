-- 실패 회차가 자산을 영구 차단하는 문제 해소.
-- 관리자가 DEAD_LETTER 지급 건을 전부 ABANDONED 처리하면 배치가 새 종결 상태 CLOSED_ABANDONED로 마감
-- 자산당 진행 중 배치를 1개로 제한하는 uk_settlement_batches_asset_in_progress의 조건을
-- "status <> 'COMPLETED'"에서 "COMPLETED와 CLOSED_ABANDONED 둘 다 진행 중 아님"으로 넓혀야
-- 마감된 배치가 다음 회차 개시를 막지 않는다.
--
CREATE UNIQUE INDEX uk_settlement_batches_asset_in_progress_v2
    ON p_settlement_batches (asset_id)
    WHERE is_deleted = false AND status NOT IN ('COMPLETED', 'CLOSED_ABANDONED');

DROP INDEX uk_settlement_batches_asset_in_progress;

ALTER INDEX uk_settlement_batches_asset_in_progress_v2 RENAME TO uk_settlement_batches_asset_in_progress;

-- 포기 처리 증빙 컬럼. "ABANDONED = 미지급"으로 오해되지 않도록, 관리자가 이미 다른 방법(은행 송금 등)으로 실제 지급을 완료했다는 증빙을 구조화된 필드로 남김
ALTER TABLE p_dividend_payouts
    ADD COLUMN resolution_type VARCHAR(20),
    ADD COLUMN resolution_reference VARCHAR(200),
    ADD COLUMN resolution_note VARCHAR(500);

-- 최종 정산(원금반환)도 같은 문제(투자자 몫이 방치되면 안 됨)가 동일하게 적용되어 함께 도입
ALTER TABLE p_final_settlement_payouts
    ADD COLUMN resolution_type VARCHAR(20),
    ADD COLUMN resolution_reference VARCHAR(200),
    ADD COLUMN resolution_note VARCHAR(500);