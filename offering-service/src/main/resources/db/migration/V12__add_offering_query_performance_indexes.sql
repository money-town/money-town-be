-- =========================================================
-- 공모 제목 부분 검색
-- =========================================================

-- QueryDSL containsIgnoreCase의
-- LOWER(title) LIKE '%keyword%' 검색을 GIN Trigram 인덱스로 처리한다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 동일한 LOWER(title) 표현식으로 인덱스를 생성한다.
CREATE INDEX idx_offerings_active_title_trgm
    ON p_offerings
    USING GIN (LOWER(title) gin_trgm_ops)
    WHERE is_deleted = FALSE;

-- =========================================================
-- 공모 상태 전환 스케줄러
-- =========================================================
-- 스케줄러가 주기적으로 상태 변경 대상을 찾을 때 전체 테이블을 반복 조회하지 않게 하기 위함

-- SCHEDULED → OPEN 대상 검색
CREATE INDEX idx_offerings_scheduled_start
    ON p_offerings (start_at)
    WHERE offering_status = 'SCHEDULED'
      AND is_deleted = FALSE;

-- SOLD_OUT → CLOSED 대상 검색
CREATE INDEX idx_offerings_sold_out_end
    ON p_offerings (end_at)
    WHERE offering_status = 'SOLD_OUT'
      AND is_deleted = FALSE;

-- =========================================================
-- 공모 목록 조회
-- =========================================================

-- 이슈어별 공모 목록의 기본 정렬
CREATE INDEX idx_offerings_active_issuer_created
    ON p_offerings (
                    issuer_id,
                    created_at DESC,
                    offering_id DESC
        )
    WHERE is_deleted = FALSE;

-- 공모 상태별 목록의 기본 정렬
CREATE INDEX idx_offerings_active_status_created
    ON p_offerings (
                    offering_status,
                    created_at DESC,
                    offering_id DESC
        )
    WHERE is_deleted = FALSE;


-- =========================================================
-- 청약 목록 조회
-- =========================================================

-- 투자자의 내 청약 목록 기본 정렬
CREATE INDEX idx_subscriptions_active_user_created
    ON p_subscriptions (
                        user_id,
                        created_at DESC,
                        subscription_id DESC
        )
    WHERE is_deleted = FALSE;