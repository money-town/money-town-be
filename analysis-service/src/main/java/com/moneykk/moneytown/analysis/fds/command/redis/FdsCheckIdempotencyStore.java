package com.moneykk.moneytown.analysis.fds.command.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.fds.command.dto.PreFdsCheckResult;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FdsCheckIdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper om;

    // 처리중 선점. false = 다른 요청이 이미 선점 완료
    public boolean tryBegin(UUID requestId){
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key(requestId), "PENDING", Duration.ofSeconds(30));
        return Boolean.TRUE.equals(ok);
    }

    // 완료된 결과가 있을 때만 present. null/Pending 이면 Empty
    public Optional<PreFdsCheckResult> find(UUID requestId){
        String v = redisTemplate.opsForValue().get(key(requestId));
        if(v == null || v.equals("PENDING")) return Optional.empty();
        try{
            return Optional.of(om.readValue(v, PreFdsCheckResult.class));
        }catch (JsonProcessingException e) {
            throw new BusinessException(AnalysisErrorCode.FDS_UNAVAILABLE);
        }
    }

    public void complete(UUID requestId, PreFdsCheckResult result){
        try{
            redisTemplate.opsForValue().set(key(requestId), om.writeValueAsString(result), Duration.ofSeconds(300));
        }catch (JsonProcessingException e) {
            throw new BusinessException(AnalysisErrorCode.FDS_UNAVAILABLE);
        }
    }

    public void abort(UUID requestId){
        redisTemplate.delete(key(requestId));
    }


    private String key(UUID requestId){
        return "fds:check:" + requestId;
    }
}
