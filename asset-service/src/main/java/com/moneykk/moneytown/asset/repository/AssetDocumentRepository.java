package com.moneykk.moneytown.asset.repository;

import com.moneykk.moneytown.asset.entity.AssetDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 자산 문서 저장소 */
public interface AssetDocumentRepository
        extends JpaRepository<AssetDocument, UUID> {
}