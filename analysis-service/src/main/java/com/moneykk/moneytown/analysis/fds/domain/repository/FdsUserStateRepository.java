package com.moneykk.moneytown.analysis.fds.domain.repository;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FdsUserStateRepository extends JpaRepository<FdsUserState, UUID> {
    Optional<FdsUserState> findByUserIdAndDeletedAtIsNull(UUID userId);
}
