package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.user.entity.RefreshToken;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import com.moneykk.moneytown.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void replaceActiveToken(UUID userId, IssuedToken refreshToken){

        refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(RefreshToken::revoke);

        refreshTokenRepository.save(RefreshToken.create(userId, refreshToken));
    }




}
