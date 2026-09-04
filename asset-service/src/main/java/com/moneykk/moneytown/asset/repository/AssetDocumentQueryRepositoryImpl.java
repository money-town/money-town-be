package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.DocumentType;
import com.moneykk.moneytown.asset.entity.QAssetDocument;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssetDocumentQueryRepositoryImpl
        implements AssetDocumentQueryRepository {

    private static final QAssetDocument document =
            QAssetDocument.assetDocument;

    private final JPAQueryFactory queryFactory;

    @Override
    public int findNextVersion(
            UUID assetId,
            DocumentType documentType
    ) {
        // 삭제된 문서도 버전 계산에 포함
        Integer latestVersion = queryFactory
                .select(document.documentVersion.max())
                .from(document)
                .where(
                        document.assetId.eq(assetId),
                        document.documentType.eq(documentType)
                )
                .fetchOne();

        return latestVersion == null
                ? 1
                : latestVersion + 1;
    }

    @Override
    public List<AssetDocument> findDocuments(
            UUID assetId,
            UUID cursor,
            int limit,
            Sort.Direction direction
    ) {
        boolean ascending = direction.isAscending();
        BooleanExpression cursorCondition = null;

        if (cursor != null) {
            // 커서 문서의 등록 시간 조회
            Instant cursorCreatedAt = queryFactory
                    .select(document.createdAt)
                    .from(document)
                    .where(
                            document.id.eq(cursor),
                            document.assetId.eq(assetId),
                            document.deleted.isFalse()
                    )
                    .fetchOne();

            if (cursorCreatedAt == null) {
                throw new BusinessException(
                        AssetErrorCode.INVALID_ASSET_DOCUMENT_CURSOR
                );
            }

            cursorCondition = ascending
                    ? document.createdAt.gt(cursorCreatedAt)
                    .or(
                            document.createdAt.eq(cursorCreatedAt)
                                    .and(document.id.gt(cursor))
                    )
                    : document.createdAt.lt(cursorCreatedAt)
                    .or(
                            document.createdAt.eq(cursorCreatedAt)
                                    .and(document.id.lt(cursor))
                    );
        }

        return queryFactory
                .selectFrom(document)
                .where(
                        document.assetId.eq(assetId),
                        document.deleted.isFalse(),
                        cursorCondition
                )
                .orderBy(
                        ascending
                                ? document.createdAt.asc()
                                : document.createdAt.desc(),
                        ascending
                                ? document.id.asc()
                                : document.id.desc()
                )
                .limit(limit)
                .fetch();
    }
}