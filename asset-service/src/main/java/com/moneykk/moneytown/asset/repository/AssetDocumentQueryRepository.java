package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.DocumentType;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetDocumentQueryRepository {

    // 다음 문서 버전 조회
    int findNextVersion(
            UUID assetId,
            DocumentType documentType
    );

    // 삭제되지 않은 문서 목록 조회
    List<AssetDocument> findDocuments(
            UUID assetId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    );

    // 삭제되지 않은 문서 단건 조회
    Optional<AssetDocument> findActiveById(
            UUID assetId,
            UUID documentId
    );
}