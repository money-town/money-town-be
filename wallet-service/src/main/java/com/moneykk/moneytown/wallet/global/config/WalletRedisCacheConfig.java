package com.moneykk.moneytown.wallet.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// 지갑 잔액 조회 Redis 캐시 설정. TTL은 짧게 잡아 무효화 누락 시에도 오래된 잔액이 노출되는 시간을 최소화한다.
@Configuration
public class WalletRedisCacheConfig {

    public static final String WALLET_CACHE = "wallet";

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        Jackson2JsonRedisSerializer<WalletResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, WalletResponse.class);

        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(CACHE_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));

        RedisCacheConfiguration walletConfiguration = defaultConfiguration
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withCacheConfiguration(WALLET_CACHE, walletConfiguration)
                .build();
    }
}
