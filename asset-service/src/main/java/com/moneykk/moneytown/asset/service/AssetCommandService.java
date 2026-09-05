package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.request.AssetUpdateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

import java.util.UUID;

/**
 * 자산 등록·변경 서비스
 */
@Service
@RequiredArgsConstructor
public class AssetCommandService {

    private static final long MAX_IMAGE_SIZE =
            10L * 1024 * 1024;

    private final AssetRepository assetRepository;
    private final AssetQueryRepository assetQueryRepository;
    private final S3StorageService s3StorageService;

    /**
     * 자산 등록
     */
    @Transactional
    public AssetCreateResponse createAsset(
            UUID userId,
            String role,
            AssetCreateRequest request
    ) {
        // 자산운용자와 관리자만 등록 가능
        if (!"ISSUER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_CREATE_ACCESS_DENIED
            );
        }

        // 요청한 사용자를 소유자로 설정
        Asset asset = new Asset(
                userId,
                request.assetName(),
                request.type(),
                request.description(),
                request.valuationAmount(),
                request.expectedReturnRate(),
                request.detailData(),
                request.totalShareQuantity()
        );

        // DRAFT 상태로 저장
        Asset savedAsset = assetRepository.save(asset);

        // 저장 결과 반환
        return AssetCreateResponse.from(savedAsset);
    }

    /**
     * 자산 정보 수정
     */
    @Transactional
    public void updateAsset(
            UUID assetId,
            UUID userId,
            String role,
            AssetUpdateRequest request
    ) {
        // 자산운용자와 관리자만 수정 가능
        if (userId == null
                || (!"ISSUER".equals(role) && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        // 잠금 조회로 동시 수정에 따른 변경 유실 방지
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 관리자가 아니면 본인이 등록한 자산만 수정 가능
        if (!"ADMIN".equals(role) && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        // 평가금액은 수정 불가. null이나 기존과 같은 값도 요청에서 제외해야 한다.
        if (request.detail() != null && request.detail().containsKey("appraisalAmount")) {
            throw new BusinessException(AssetErrorCode.APPRAISAL_AMOUNT_UPDATE_NOT_ALLOWED);
        }

        // 상태 검사와 단가·차액 재계산은 엔티티에서 처리
        asset.updateInfo(
                request.name(),
                request.description(),
                request.ownerName(),
                request.detail(),
                null, // 기존 평가금액 유지. 단가·차액 재계산 로직은 그대로 사용
                request.totalShareQuantity()
        );

        // JPA 변경 감지로 저장
    }

    /**
     * 자산 상태 변경
     */
    @Transactional
    public void changeAssetStatus(
            UUID assetId,
            UUID userId,
            String role,
            AssetStatus nextStatus,
            String rejectionReason
    ) {
        // 상태 변경은 자산운용자와 관리자만 가능
        if (userId == null
                || (!"ISSUER".equals(role) && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_STATUS_CHANGE_ACCESS_DENIED
            );
        }

        // 동시 상태 변경을 막기 위해 잠금 조회
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인 자산의 심사 요청만 가능
        boolean issuerAllowed =
                "ISSUER".equals(role)
                        && userId.equals(asset.getUserId())
                        && nextStatus == AssetStatus.REVIEW_REQUESTED;

        // 관리자는 승인·반려·중단만 가능
        boolean adminAllowed =
                "ADMIN".equals(role)
                        && (nextStatus == AssetStatus.APPROVED
                        || nextStatus == AssetStatus.REJECTED
                        || nextStatus == AssetStatus.SUSPENDED);

        if (!issuerAllowed && !adminAllowed) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_STATUS_CHANGE_ACCESS_DENIED
            );
        }

        // 실제 상태 전이는 엔티티에서 검증
        asset.changeStatus(nextStatus, rejectionReason);
    }

    /**
     * 자산 삭제
     */
    @Transactional
    public void deleteAsset(
            UUID assetId,
            UUID userId,
            String role
    ) {
        // 자산운용자와 관리자만 삭제 가능
        if (userId == null
                || (!"ISSUER".equals(role) && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DELETE_ACCESS_DENIED
            );
        }

        // 삭제되지 않은 자산을 잠금 조회
        Asset asset = assetQueryRepository.findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인이 등록한 자산만 삭제 가능
        if (!"ADMIN".equals(role)
                && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DELETE_ACCESS_DENIED
            );
        }

        // 상태 확인 후 소프트 삭제
        asset.delete(userId);
    }

    /**
     * 자산 대표 이미지 등록·변경
     */
    @Transactional
    public void setRepresentativeImage(
            UUID assetId,
            UUID userId,
            String role,
            MultipartFile file
    ) {
        // 자산운용자와 관리자만 등록 가능
        if (userId == null
                || (!"ISSUER".equals(role)
                && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        // 삭제되지 않은 자산을 잠금 조회
        Asset asset = assetQueryRepository
                .findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산운용자는 본인 자산만 변경 가능
        if (!"ADMIN".equals(role)
                && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED
            );
        }

        byte[] content = readImage(file);
        String contentType = file.getContentType();

        validateImage(content, contentType);

        String newObjectKey = "assets/"
                + assetId
                + "/representative/"
                + UUID.randomUUID();

        String previousObjectKey =
                asset.getRepresentativeImageKey();

        // DB 롤백 시 새로 업로드한 이미지 제거
        s3StorageService.uploadWithRollbackCleanup(
                newObjectKey,
                content,
                contentType
        );

        // 최초 등록 또는 기존 이미지 키 변경
        asset.updateRepresentativeImage(newObjectKey);

        // 기존 이미지가 있으면 DB 커밋 후 삭제
        s3StorageService.deleteAfterCommit(
                previousObjectKey
        );
    }

    /**
     * 이미지 파일 읽기
     */
    private byte[] readImage(MultipartFile file) {
        if (file == null
                || file.isEmpty()
                || file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_IMAGE
            );
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_STORAGE_FAILED
            );
        }
    }

    /**
     * PNG와 JPEG 파일 내용 검증
     */
    private void validateImage(
            byte[] content,
            String contentType
    ) {
        if (contentType == null) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_IMAGE
            );
        }

        boolean valid = switch (
                contentType.toLowerCase(Locale.ROOT)
                ) {
            case "image/png" -> startsWith(
                    content,
                    0x89, 0x50, 0x4E, 0x47
            );

            case "image/jpeg" -> startsWith(
                    content,
                    0xFF, 0xD8, 0xFF
            );

            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_IMAGE
            );
        }
    }

    /**
     * 파일 헤더 확인
     */
    private boolean startsWith(
            byte[] content,
            int... signature
    ) {
        if (content.length < signature.length) {
            return false;
        }

        for (int index = 0;
             index < signature.length;
             index++) {
            if ((content[index] & 0xFF)
                    != signature[index]) {
                return false;
            }
        }

        return true;
    }

}
