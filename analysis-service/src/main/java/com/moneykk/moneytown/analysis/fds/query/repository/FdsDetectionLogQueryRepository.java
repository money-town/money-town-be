package com.moneykk.moneytown.analysis.fds.query.repository;

import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface FdsDetectionLogQueryRepository {
    Page<FdsDetectionLog> search(FdsDetectionLogSearchCondition condition, Pageable pageable);
}
