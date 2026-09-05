package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.HoldingAdjustmentRequest;
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

    /**
     * 관리자 보유지분 수량 조정
     */
    @Transactional
    public void adjust(
            UUID holdingId,
            UUID adminId,
            String role,
            HoldingAdjustmentRequest request
    ) {
        // 관리자만 조정 가능
        if (adminId == null || !"ADMIN".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.HOLDING_ADJUSTMENT_ACCESS_DENIED
            );
        }

        // 자산 ID 조회
        UUID assetId = holdingQueryRepository
                .findAssetIdByHoldingId(holdingId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_NOT_FOUND
                ));

        // 자산 전체 배정량 변경을 위해 잠금
        Asset asset = assetQueryRepository
                .findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 사용자 보유지분 변경을 위해 잠금
        Holding holding = holdingQueryRepository
                .findByIdForUpdate(holdingId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_NOT_FOUND
                ));

        // 같은 요청이 이미 처리되었는지 확인
        Optional<HoldingHistory> existingHistory =
                holdingHistoryRepository.findByIdempotencyKey(
                        request.idempotencyKey()
                );

        if (existingHistory.isPresent()) {
            HoldingHistory history = existingHistory.get();

            // 같은 요청이면 추가 처리하지 않음
            if (history.getHoldingId().equals(holdingId)
                    && history.getBalanceAfter()
                    == request.targetQuantity()) {
                return;
            }

            // 같은 키로 다른 내용을 요청한 경우
            throw new BusinessException(
                    AssetErrorCode.HOLDING_DATA_CONFLICT
            );
        }

        long balanceBefore = holding.getQuantity();
        long balanceAfter = request.targetQuantity();

        // 수량 변화가 없으면 조정할 필요 없음
        if (balanceBefore == balanceAfter) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_HOLDING_QUANTITY
            );
        }

        long changedQuantity =
                Math.abs(balanceAfter - balanceBefore);

        // 자산 전체 배정 수량도 함께 변경
        if (balanceAfter > balanceBefore) {
            asset.allocateShares(changedQuantity);
        } else {
            asset.revokeShares(changedQuantity);
        }

        // 사용자 보유 수량 변경
        holding.adjust(balanceAfter);

        // 조정 이력 저장
        HoldingHistory history = new HoldingHistory(
                holdingId,
                null,
                HoldingHistoryType.ADJUSTMENT,
                changedQuantity,
                balanceBefore,
                balanceAfter,
                request.idempotencyKey(),
                request.reason().trim()
        );

        holdingHistoryRepository.save(history);
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
