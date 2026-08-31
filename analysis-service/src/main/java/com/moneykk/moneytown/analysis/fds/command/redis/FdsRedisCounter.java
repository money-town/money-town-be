package com.moneykk.moneytown.analysis.fds.command.redis;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.global.config.FdsRuleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FdsRedisCounter {

    private final StringRedisTemplate redisTemplate;
    private final FdsRuleProperties ruleProperties;

    private static final RedisScript<List> COUNT_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/fds_count.lua"), List.class);

    public FdsCounts recordAndCount(UUID userId, UUID requestId, UUID assetId){
        long rapidMs = ruleProperties.get(RuleCode.RAPID_REQUEST).windowSeconds() * 1000L;
        long burstMs = ruleProperties.get(RuleCode.BURST_REQUEST).windowSeconds() * 1000L;

        List<Long> r = redisTemplate.execute(
                COUNT_SCRIPT,
                List.of("fds:req:" + userId, "fds:offering:" + userId),
                String.valueOf(System.currentTimeMillis()),
                requestId.toString(),
                assetId.toString(),
                String.valueOf(rapidMs),
                String.valueOf(burstMs),
                "60"    //ttlSec
        );
        return new FdsCounts(r.get(0), r.get(1), r.get(2));
    }

}
