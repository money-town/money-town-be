package com.moneykk.moneytown.user.service;

import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final String KEY_PREFIX = "auth:refresh:";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 사용자별 활성 Refresh Token의 jti 하나만 TTL과 함께 저장한다. */
    public void replaceActiveToken(UUID userId, IssuedToken refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken.tokenId(),
                remainingTtl(refreshToken)
        );
    }

    public boolean isActiveToken(UUID userId, String tokenId) {
        return tokenId.equals(redisTemplate.opsForValue().get(key(userId)));
    }

    /** 기존 jti가 일치할 때만 새 jti로 원자적으로 교체해 동시 재발급을 막는다. */
    public boolean rotateActiveToken(UUID userId, String currentTokenId, IssuedToken newRefreshToken) {
        Duration ttl = remainingTtl(newRefreshToken);
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                currentTokenId,
                newRefreshToken.tokenId(),
                Long.toString(ttl.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    public void revokeActiveToken(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private Duration remainingTtl(IssuedToken refreshToken) {
        Duration ttl = Duration.between(Instant.now(), refreshToken.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("만료된 Refresh Token은 저장할 수 없습니다.");
        }
        return ttl;
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
