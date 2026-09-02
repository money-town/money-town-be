package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.request.HoldingRevocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResult;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingRevocationResult;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.*;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 지분 배정·회수 서비스
 */
@Service
@RequiredArgsConstructor
public class HoldingCommandService {

    private final AssetQueryRepository assetQueryRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingHistoryRepository holdingHistoryRepository;
    private final HoldingQueryRepository holdingQueryRepository;

    @Transactional
    public HoldingAllocationResponse allocate(HoldingAllocationRequest request) {
        // 중복 청약 확인
        Optional<HoldingHistory> existingHistory =
                holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                        request.subscriptionId(),
                        HoldingHistoryType.ALLOCATE
                );

        if (existingHistory.isPresent()) {
            return alreadyProcessed(request, existingHistory.get());
        }

        // 자산 조회
        Asset asset = assetQueryRepository
                .findActiveByIdForUpdate(request.assetId())
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산 락을 기다리는 동안 같은 청약이 처리됐는지 다시 확인
        existingHistory = holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                request.subscriptionId(),
                HoldingHistoryType.ALLOCATE
        );
        if (existingHistory.isPresent()) {
            return alreadyProcessed(request, existingHistory.get());
        }

        // 자산 배정 수량 증가
        asset.allocateShares(request.quantity());

        // 기존 보유지분 조회 또는 생성
        Holding holding = holdingRepository
                .findByAssetIdAndUserId(request.assetId(), request.userId())
                .orElseGet(() -> new Holding(
                        request.assetId(),
                        request.userId(),
                        0
                ));

        long balanceBefore = holding.getQuantity();

        // 사용자 보유 수량 증가
        holding.allocate(request.quantity());
        holding = holdingRepository.save(holding);

        // 배정 이력 저장
        HoldingHistory history = new HoldingHistory(
                holding.getId(),
                request.subscriptionId(),
                HoldingHistoryType.ALLOCATE,
                request.quantity(),
                balanceBefore,
                holding.getQuantity(),
                "ALLOCATE:" + request.subscriptionId(),
                null
        );

        holdingHistoryRepository.save(history);

        return new HoldingAllocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                request.quantity(),
                HoldingAllocationResult.ALLOCATED
        );
    }

    private HoldingAllocationResponse alreadyProcessed(
            HoldingAllocationRequest request,
            HoldingHistory history
    ) {
        Holding holding = holdingRepository.findById(history.getHoldingId())
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                ));

        // 기존 처리 내용과 현재 요청이 같은지 확인
        if (!holding.getAssetId().equals(request.assetId())
                || !holding.getUserId().equals(request.userId())
                || history.getQuantity() != request.quantity()) {
            throw new BusinessException(
                    AssetErrorCode.HOLDING_DATA_CONFLICT
            );
        }

        return new HoldingAllocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                history.getQuantity(),
                HoldingAllocationResult.ALREADY_PROCESSED
        );
    }

    @Transactional
    public HoldingRevocationResponse revoke(
            UUID holdingId,
            HoldingRevocationRequest request
    ) {
        // 이미 회수된 청약인지 확인
        Optional<HoldingHistory> existingHistory =
                holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                        request.subscriptionId(),
                        HoldingHistoryType.REVOKE
                );

        if (existingHistory.isPresent()) {
            return alreadyRevoked(holdingId, request, existingHistory.get());
        }

        // 해당 청약의 기존 배정 이력 조회
        Optional<HoldingHistory> allocationHistoryOptional =
                holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                        request.subscriptionId(),
                        HoldingHistoryType.ALLOCATE
                );

        // 배정한 이력이 없으면 회수할 지분도 없음
        if (allocationHistoryOptional.isEmpty()) {
            return noAction(holdingId, request);
        }

        // 기존 배정 이력 꺼내기
        HoldingHistory allocationHistory =
                allocationHistoryOptional.get();

        // 요청한 보유지분과 실제 배정 이력이 같은지 확인
        if (!allocationHistory.getHoldingId().equals(holdingId)) {
            throw new BusinessException(AssetErrorCode.HOLDING_DATA_CONFLICT);
        }

        // 자산 ID 조회
        UUID assetId = holdingQueryRepository
                .findAssetIdByHoldingId(holdingId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                ));

        // 자산을 조회하면서 비관적 락 획득
        Asset asset = assetQueryRepository
                .findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 락을 기다리는 동안 회수가 처리됐는지 다시 확인
        existingHistory =
                holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                        request.subscriptionId(),
                        HoldingHistoryType.REVOKE
                );

        if (existingHistory.isPresent()) {
            return alreadyRevoked(holdingId, request, existingHistory.get());
        }

        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                ));

        long quantity = allocationHistory.getQuantity();
        long balanceBefore = holding.getQuantity();

        // 자산 배정 수량과 사용자 보유 수량 감소
        asset.revokeShares(quantity);
        holding.revoke(quantity);

        holdingRepository.save(holding);

        HoldingHistory revocationHistory = new HoldingHistory(
                holding.getId(),
                request.subscriptionId(),
                HoldingHistoryType.REVOKE,
                quantity,
                balanceBefore,
                holding.getQuantity(),
                "REVOKE:" + request.subscriptionId(),
                request.reason()
        );

        holdingHistoryRepository.save(revocationHistory);

        return new HoldingRevocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                quantity,
                HoldingRevocationResult.REVOKED
        );
    }

    private HoldingRevocationResponse alreadyRevoked(
            UUID holdingId,
            HoldingRevocationRequest request,
            HoldingHistory history
    ) {
        // 기존 회수 이력의 지분과 요청한 지분 비교
        if (!history.getHoldingId().equals(holdingId)) {
            throw new BusinessException(AssetErrorCode.HOLDING_DATA_CONFLICT);
        }

        // 기존 보유지분 조회
        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                ));

        return new HoldingRevocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                history.getQuantity(),
                HoldingRevocationResult.NO_ACTION
        );
    }

    private HoldingRevocationResponse noAction(
        UUID holdingId,
        HoldingRevocationRequest request
    ) {
        // 실제로 회수한 지분이 없으므로 수량은 0
        return new HoldingRevocationResponse(
                request.subscriptionId(),
                holdingId,
                null,
                null,
                0,
                HoldingRevocationResult.NO_ACTION
        );
    }
}
