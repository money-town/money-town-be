package com.moneykk.moneytown.analysis.fds.domain.repository;

import com.moneykk.moneytown.analysis.fds.domain.FdsDetectionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FdsDetectionLogRepository extends JpaRepository<FdsDetectionLog, UUID> {
    boolean existsByEventId(UUID eventId);
}
