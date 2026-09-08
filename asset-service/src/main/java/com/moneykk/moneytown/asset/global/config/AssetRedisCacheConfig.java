package com.moneykk.moneytown.asset.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/** 자산 상세 조회 Redis 캐시 설정 */
@Configuration
public class AssetRedisCacheConfig {

    public static final String ASSET_DETAIL_CACHE =
            "assetDetail";

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        Jackson2JsonRedisSerializer<AssetDetailResponse>
                valueSerializer =
                new Jackson2JsonRedisSerializer<>(
                        objectMapper,
                        AssetDetailResponse.class
                );

        RedisCacheConfiguration defaultConfiguration =
                RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(CACHE_TTL)
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        );

        RedisCacheConfiguration assetDetailConfiguration =
                defaultConfiguration.serializeValuesWith(
                        RedisSerializationContext
                                .SerializationPair
                                .fromSerializer(valueSerializer)
                );

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withCacheConfiguration(
                        ASSET_DETAIL_CACHE,
                        assetDetailConfiguration
                )
                .build();
    }
}