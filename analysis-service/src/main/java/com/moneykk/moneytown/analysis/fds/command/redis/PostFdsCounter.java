package com.moneykk.moneytown.analysis.fds.command.redis;

import com.moneykk.moneytown.analysis.fds.domain.EventType;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.global.config.PostFdsRuleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostFdsCounter {

    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("redis/post_fds_count.lua"), List.class);

    private static final long FAIL_LIMIT_TTL_SEC = 300L;
    private static final long RECENT_TTL_SEC = 86_400L;

    private final StringRedisTemplate redisTemplate;
    private final PostFdsRuleProperties ruleProperties;

    public PostFdsCounts recordAndCount(UUID userId, UUID eventId, EventType eventType, Instant occurredAt){
        long failWindowMs = ruleProperties.get(RuleCode.REPEATED_FAILURE).windowSeconds() * 1000L;
        long limitWindowMs = ruleProperties.get(RuleCode.REPEATED_LIMIT_EXCEEDED).windowSeconds() * 1000L;
        int sampleSize = ruleProperties.get(RuleCode.HIGH_CANCEL_RATE).sampleSize();

        List<Long> r = redisTemplate.execute(
                SCRIPT,
                List.of(
                        "fds:post:fail:{" + userId + "}",
                        "fds:post:limit:{" + userId + "}",
                        "fds:post:recent:{" + userId + "}"
                ),
                String.valueOf(occurredAt.toEpochMilli()),
                eventId.toString(),
                eventType.name(),
                String.valueOf(failWindowMs),
                String.valueOf(limitWindowMs),
                String.valueOf(sampleSize),
                String.valueOf(FAIL_LIMIT_TTL_SEC),
                String.valueOf(RECENT_TTL_SEC)
        );
        return new PostFdsCounts(r.get(0), r.get(1), r.get(2), r.get(3));
    }

    public void clear(UUID userId){
        redisTemplate.delete(List.of(
                "fds:post:fail:{" + userId + "}",
                "fds:post:limit:{" + userId + "}",
                "fds:post:recent:{" + userId + "}"
        ));
    }
}
