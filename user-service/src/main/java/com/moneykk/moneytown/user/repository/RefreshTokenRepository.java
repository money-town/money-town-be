package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
