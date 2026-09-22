-- AI 포트폴리오 생성 완료/실패 시 DM을 받을 Slack 사용자 ID를 저장한다.
-- CreatePortfolioRequest에서 필수(@NotNull)로 받지만, 기존 행은 값이 없으므로
-- 컬럼 자체는 nullable로 둔다 (엔티티 @Column도 nullable 미지정 = nullable=true).

ALTER TABLE p_ai_portfolios
    ADD COLUMN slack_id VARCHAR(50);