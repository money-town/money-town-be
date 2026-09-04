package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.AssetDocumentCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentDownloadResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListItemResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.DocumentType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetDocumentQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetDocumentRepository;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 자산 문서 서비스
 */
@Service
@RequiredArgsConstructor
public class AssetDocumentService {

    private static final long MAX_FILE_SIZE =
            10L * 1024 * 1024;

    private final AssetQueryRepository assetQueryRepository;
    private final AssetDocumentRepository assetDocumentRepository;
    private final AssetDocumentQueryRepository assetDocumentQueryRepository;
    private final S3StorageService s3StorageService;

    /**
     * 자산 문서 등록
     */
    @Transactional
    public AssetDocumentCreateResponse createDocument(
            UUID assetId,
            UUID userId,
            String role,
            DocumentType documentType,
            MultipartFile file
    ) {
        // 권한과 자산 소유권 확인
        validateDocumentManageAccess(
                assetId,
                userId,
                role
        );

        // 파일 내용과 기본 정보 읽기
        byte[] content = readFile(file);
        String contentType = file.getContentType();
        String originalFilename = cleanFilename(
                file.getOriginalFilename()
        );

        // 파일 크기와 형식 확인
        validateFile(
                content,
                contentType,
                originalFilename
        );

        // 같은 자산·문서 유형의 다음 버전 계산
        int nextVersion =
                assetDocumentQueryRepository.findNextVersion(
                        assetId,
                        documentType
                );

        // 원본 파일명을 경로에 사용하지 않고 UUID로 생성
        String objectKey = "assets/"
                + assetId
                + "/documents/"
                + UUID.randomUUID();

        AssetDocument document = new AssetDocument(
                assetId,
                documentType,
                nextVersion,
                originalFilename,
                objectKey,
                contentType,
                content.length,
                sha256(content)
        );

        // DB 제약조건을 먼저 확인
        AssetDocument saved =
                assetDocumentRepository.saveAndFlush(document);

        // DB 저장 성공 후 S3에 파일 업로드
        s3StorageService.upload(
                objectKey,
                content,
                contentType
        );

        return AssetDocumentCreateResponse.from(saved);
    }

    /**
     * 자산 문서 목록 조회
     */
    @Transactional(readOnly = true)
    public AssetDocumentListResponse getDocuments(
            UUID assetId,
            UUID userId,
            String role,
            UUID cursor,
            int size,
            Sort.Direction direction
    ) {
        // 자산 문서 조회 권한 확인
        validateDocumentReadAccess(
                assetId,
                userId,
                role
        );

        // 다음 페이지 확인을 위해 한 건 더 조회
        List<AssetDocument> documents =
                assetDocumentQueryRepository.findDocuments(
                        assetId,
                        cursor,
                        size + 1,
                        direction
                );

        boolean hasNext = documents.size() > size;

        List<AssetDocumentListItemResponse> items =
                documents.stream()
                        .limit(size)
                        .map(AssetDocumentListItemResponse::from)
                        .toList();

        UUID nextCursor = hasNext
                ? items.get(items.size() - 1).documentId()
                : null;

        return new AssetDocumentListResponse(
                items,
                nextCursor,
                hasNext
        );
    }

    /**
     * 문서 다운로드 URL 발급
     */
    @Transactional(readOnly = true)
    public AssetDocumentDownloadResponse createDownloadUrl(
            UUID assetId,
            UUID documentId,
            UUID userId,
            String role
    ) {
        // 문서 조회 권한 확인
        validateDocumentReadAccess(
                assetId,
                userId,
                role
        );

        AssetDocument document =
                assetDocumentQueryRepository.findActiveById(
                        assetId,
                        documentId
                ).orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_DOCUMENT_NOT_FOUND
                ));

        // 실제 URL 만료 시각보다 조금 이르게 안내
        Instant expiresAt = Instant.now()
                .plus(Duration.ofMinutes(10));

        String downloadUrl =
                s3StorageService.createDownloadUrl(
                        document.getS3ObjectKey()
                );

        return new AssetDocumentDownloadResponse(
                document.getId(),
                document.getOriginalFilename(),
                downloadUrl,
                expiresAt
        );
    }

    /**
     * 자산 문서 삭제
     */
    @Transactional
    public void deleteDocument(
            UUID assetId,
            UUID documentId,
            UUID userId,
            String role
    ) {
        // 관리자 또는 자산 소유자인지 확인
        validateDocumentManageAccess(
                assetId,
                userId,
                role
        );

        // 해당 자산의 삭제되지 않은 문서 조회
        AssetDocument document =
                assetDocumentQueryRepository.findActiveById(
                        assetId,
                        documentId
                ).orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_DOCUMENT_NOT_FOUND
                ));

        // DB에서 소프트 삭제 처리
        document.softDelete(userId);

        // S3 파일 삭제
        s3StorageService.delete(
                document.getS3ObjectKey()
        );
    }

    /**
     * 문서 등록·삭제 권한 확인
     */
    private void validateDocumentManageAccess(
            UUID assetId,
            UUID userId,
            String role
    ) {
        if (userId == null
                || (!"ISSUER".equals(role)
                && !"ADMIN".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED
            );
        }

        // 등록 버전 충돌과 동시 삭제를 막기 위해 자산 잠금
        Asset asset = assetQueryRepository
                .findActiveByIdForUpdate(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 운용자는 본인이 등록한 자산만 관리 가능
        if (!"ADMIN".equals(role)
                && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED
            );
        }
    }

    /**
     * 문서 조회 권한 확인
     */
    private void validateDocumentReadAccess(
            UUID assetId,
            UUID userId,
            String role
    ) {
        if (userId == null
                || (!"ADMIN".equals(role)
                && !"ISSUER".equals(role)
                && !"INVESTOR".equals(role))) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED
            );
        }

        Asset asset = assetQueryRepository
                .findActiveById(assetId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 운용자는 본인이 등록한 자산만 조회 가능
        if ("ISSUER".equals(role)
                && !userId.equals(asset.getUserId())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED
            );
        }

        // 투자자는 승인된 자산만 조회 가능
        if ("INVESTOR".equals(role)
                && asset.getStatus() != AssetStatus.APPROVED) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED
            );
        }
    }

    /**
     * 파일 읽기
     */
    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_DOCUMENT
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
     * 파일명 정리
     */
    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_DOCUMENT
            );
        }

        String cleaned = StringUtils.cleanPath(filename);

        if (cleaned.contains("..")
                || cleaned.length() > 255) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_DOCUMENT
            );
        }

        return cleaned;
    }

    /**
     * 파일 형식 검증
     */
    private void validateFile(
            byte[] content,
            String contentType,
            String filename
    ) {
        if (content.length == 0
                || content.length > MAX_FILE_SIZE
                || contentType == null) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_DOCUMENT
            );
        }

        String type = contentType.toLowerCase(Locale.ROOT);
        String name = filename.toLowerCase(Locale.ROOT);

        boolean valid = switch (type) {
            case "application/pdf" -> name.endsWith(".pdf")
                    && startsWith(
                    content,
                    0x25, 0x50, 0x44, 0x46
            );

            case "image/png" -> name.endsWith(".png")
                    && startsWith(
                    content,
                    0x89, 0x50, 0x4E, 0x47
            );

            case "image/jpeg" -> (name.endsWith(".jpg")
                    || name.endsWith(".jpeg"))
                    && startsWith(
                    content,
                    0xFF, 0xD8, 0xFF
            );

            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_DOCUMENT
            );
        }
    }

    /**
     * 파일 헤더 비교
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

    /**
     * SHA-256 해시 생성
     */
    private String sha256(byte[] content) {
        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(content);

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}