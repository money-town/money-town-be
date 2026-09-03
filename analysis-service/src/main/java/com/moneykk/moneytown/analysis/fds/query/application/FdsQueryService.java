package com.moneykk.moneytown.analysis.fds.query.application;

import com.moneykk.moneytown.analysis.fds.domain.FdsUserState;
import com.moneykk.moneytown.analysis.fds.domain.repository.FdsUserStateRepository;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogResponse;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsDetectionLogSearchCondition;
import com.moneykk.moneytown.analysis.fds.query.dto.FdsUserStateResponse;
import com.moneykk.moneytown.analysis.fds.query.repository.FdsDetectionLogQueryRepository;
import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class FdsQueryService {

    private final FdsUserStateRepository fdsUserStateRepository;
    private final FdsDetectionLogQueryRepository fdsDetectionLogQueryRepository;

    public FdsUserStateResponse getUserState(UUID userId) {
        FdsUserState userState = fdsUserStateRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.FDS_STATE_NOT_FOUND));

        return FdsUserStateResponse.from(userState);
    }

    public PageResponse<FdsDetectionLogResponse> getDetectionLogs(FdsDetectionLogSearchCondition searchCondition, Pageable pageable) {
        return PageResponse.from(fdsDetectionLogQueryRepository.search(searchCondition, pageable), FdsDetectionLogResponse::from);
    }
}
