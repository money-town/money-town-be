-- 최종 정산 COMPLETED 이후 자산 서비스에 종료 완료를 통보한 시각을 기록한다.
-- NULL이면 아직 통보에 성공하지 못한 것으로 간주해 스케줄러가 재호출한다(자산 서비스 API는 멱등).
ALTER TABLE p_final_settlement_batches
    ADD COLUMN asset_termination_completed_at TIMESTAMPTZ;