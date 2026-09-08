package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.DocumentType;

import java.time.Instant;
import java.util.UUID;

/** 자산 문서 등록 결과 */
public record AssetDocumentCreateResponse(

        // 문서 ID
        UUID documentId,

        // 연결된 자산 ID
        UUID assetId,

        // 문서 유형
        DocumentType documentType,

        // 문서 버전
        int documentVersion,

        // 원본 파일명
        String originalFilename,

        // 파일 크기
        long fileSize,

        // 등록 시간
        Instant createdAt
) {

    /** 저장된 문서를 응답으로 변환 */
    public static AssetDocumentCreateResponse from(
            AssetDocument document
    ) {
        return new AssetDocumentCreateResponse(
                document.getId(),
                document.getAssetId(),
                document.getDocumentType(),
                document.getDocumentVersion(),
                document.getOriginalFilename(),
                document.getFileSize(),
                document.getCreatedAt()
        );
    }
}