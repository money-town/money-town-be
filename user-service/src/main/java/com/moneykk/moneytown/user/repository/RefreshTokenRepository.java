package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rt
            FROM RefreshToken rt
            WHERE rt.tokenId = :tokenId
            """)
    Optional<RefreshToken> findByTokenIdForUpdate(@Param("tokenId") String tokenId);
}
