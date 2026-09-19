package com.moneykk.moneytown.analysis.fds.command.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.fds.command.dto.response.PreFdsCheckResult;
import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FdsCheckIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FdsCheckIdempotencyStore store;

    private final UUID requestId = UUID.randomUUID();
    private final String key = "fds:check:" + requestId;

    @BeforeEach
    void setUp() {
        store = new FdsCheckIdempotencyStore(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("선점에 성공하면(setIfAbsent=true) true를 반환한다")
    void tryBegin_setIfAbsentTrue_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq("PENDING"), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        assertThat(store.tryBegin(requestId)).isTrue();
    }

    @Test
    @DisplayName("이미 다른 요청이 선점했으면(setIfAbsent=false) false를 반환한다")
    void tryBegin_setIfAbsentFalse_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(store.tryBegin(requestId)).isFalse();
    }

    @Test
    @DisplayName("setIfAbsent이 null을 반환해도 false로 안전하게 처리한다")
    void tryBegin_setIfAbsentNull_returnsFalseSafely() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(null);

        assertThat(store.tryBegin(requestId)).isFalse();
    }

    @Test
    @DisplayName("저장된 값이 없으면 빈 Optional을 반환한다")
    void find_noValue_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        assertThat(store.find(requestId)).isEmpty();
    }

    @Test
    @DisplayName("선점 마커(PENDING)만 있으면 빈 Optional을 반환한다")
    void find_pendingMarker_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("PENDING");

        assertThat(store.find(requestId)).isEmpty();
    }

    @Test
    @DisplayName("완료된 결과가 저장되어 있으면 파싱해서 반환한다")
    void find_completedResult_returnsParsed() throws Exception {
        PreFdsCheckResult result = PreFdsCheckResult.block(RuleCode.RAPID_REQUEST);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(result));

        assertThat(store.find(requestId)).contains(result);
    }

    @Test
    @DisplayName("저장된 값이 손상된 JSON이면 FDS_UNAVAILABLE 예외를 던진다")
    void find_corruptedJson_throwsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("{not-valid-json");

        assertThatThrownBy(() -> store.find(requestId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_UNAVAILABLE);
    }

    @Test
    @DisplayName("완료 처리 시 결과를 직렬화해서 300초 TTL로 저장한다")
    void complete_savesSerializedResultWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        PreFdsCheckResult result = PreFdsCheckResult.pass();

        store.complete(requestId, result);

        verify(valueOperations).set(eq(key), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("직렬화에 실패하면 FDS_UNAVAILABLE 예외를 던진다")
    void complete_serializationFails_throwsUnavailable() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {});
        FdsCheckIdempotencyStore storeWithFailingMapper =
                new FdsCheckIdempotencyStore(redisTemplate, failingMapper);

        assertThatThrownBy(() -> storeWithFailingMapper.complete(requestId, PreFdsCheckResult.pass()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnalysisErrorCode.FDS_UNAVAILABLE);
    }

    @Test
    @DisplayName("abort는 선점 마커를 삭제한다")
    void abort_deletesKey() {
        store.abort(requestId);

        verify(redisTemplate).delete(key);
    }
}
