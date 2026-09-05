package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.DocumentType;

import java.time.Instant;
import java.util.UUID;

/** 자산 문서 목록 항목 */
public record AssetDocumentListItemResponse(

        UUID documentId,
        DocumentType documentType,
        int documentVersion,
        String originalFilename,
        String contentType,
        long fileSize,
        Instant createdAt

) {

    public static AssetDocumentListItemResponse from(
            AssetDocument document
    ) {
        return new AssetDocumentListItemResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentVersion(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getCreatedAt()
        );
    }
}