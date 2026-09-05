package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.command.dto.response.UnblockUserResult;
import com.moneykk.moneytown.analysis.fds.command.redis.FdsRedisCounter;
import com.moneykk.moneytown.analysis.fds.command.redis.PostFdsCounter;
import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FdsUnblockService {

    private final FdsUserStateRepository fdsUserStateRepository;
    private final FdsRedisCounter fdsRedisCounter;
    private final PostFdsCounter postFdsCounter;

    @Transactional
    public UnblockUserResult unblock(UUID userId) {

        FdsUserState userState = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.FDS_STATE_NOT_FOUND));

        userState.unblock();

        FdsUserState updateUserState = fdsUserStateRepository.save(userState);

        fdsRedisCounter.clear(userId);
        postFdsCounter.clear(userId);

        return UnblockUserResult.from(updateUserState);
    }
}
