package com.moneykk.moneytown.analysis.fds.command.application;

import com.moneykk.moneytown.analysis.fds.command.dto.UnblockUserResult;
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


    @Transactional
    public UnblockUserResult unblock(UUID userId) {

        FdsUserState userState = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.FDS_STATE_NOT_FOUND));

        userState.unblock();

        FdsUserState updateUserState = fdsUserStateRepository.save(userState);

        return UnblockUserResult.from(updateUserState);
    }
}
