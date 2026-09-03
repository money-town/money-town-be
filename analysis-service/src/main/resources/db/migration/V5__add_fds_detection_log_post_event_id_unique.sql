-- Post-FDS 이벤트 멱등성: 같은 event_id로 POST 탐지 로그가 중복 저장되지 않도록 부분 유니크 인덱스
CREATE UNIQUE INDEX uk_fds_detection_logs_post_event_id
    ON p_fds_detection_logs (event_id)
    WHERE detection_type = 'POST';