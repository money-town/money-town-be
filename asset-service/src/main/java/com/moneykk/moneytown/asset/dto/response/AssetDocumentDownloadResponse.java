package com.moneykk.moneytown.asset.dto.response;

import java.time.Instant;
import java.util.UUID;

/** 자산 문서 다운로드 URL 응답 */
public record AssetDocumentDownloadResponse(

        // 문서 ID
        UUID documentId,

        // 원본 파일명
        String originalFilename,

        // S3 임시 다운로드 URL
        String downloadUrl,

        // URL 만료 시각
        Instant expiresAt

) {
}