package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.response.AssetDocumentCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentDownloadResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetDocument;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.entity.DocumentType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetDocumentQueryRepository;
import com.moneykk.moneytown.asset.repository.AssetDocumentRepository;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetDocumentServiceTest {

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @Mock
    private AssetDocumentRepository assetDocumentRepository;

    @Mock
    private AssetDocumentQueryRepository assetDocumentQueryRepository;

    @Mock
    private S3StorageService s3StorageService;

    @InjectMocks
    private AssetDocumentService assetDocumentService;

    @Test
    @DisplayName("자산 문서를 다음 버전으로 저장하고 S3에 업로드한다")
    void createsDocument() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-04T01:00:00Z");
        byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "appraisal.pdf", "application/pdf", content);

        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findNextVersion(assetId, DocumentType.APPRAISAL))
                .thenReturn(3);
        when(assetDocumentRepository.saveAndFlush(any(AssetDocument.class)))
                .thenAnswer(invocation -> {
                    AssetDocument document = invocation.getArgument(0);
                    ReflectionTestUtils.setField(document, "id", documentId);
                    ReflectionTestUtils.setField(document, "createdAt", createdAt);
                    return document;
                });

        AssetDocumentCreateResponse response = assetDocumentService.createDocument(
                assetId, ownerId, "ISSUER", DocumentType.APPRAISAL, file);

        ArgumentCaptor<AssetDocument> documentCaptor =
                ArgumentCaptor.forClass(AssetDocument.class);
        verify(assetDocumentRepository).saveAndFlush(documentCaptor.capture());
        AssetDocument saved = documentCaptor.getValue();
        assertEquals(3, saved.getDocumentVersion());
        assertEquals("appraisal.pdf", saved.getOriginalFilename());
        assertEquals(64, saved.getFileHash().length());
        assertTrue(saved.getS3ObjectKey().startsWith("assets/" + assetId + "/documents/"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(s3StorageService).upload(
                keyCaptor.capture(), contentCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
        assertEquals(saved.getS3ObjectKey(), keyCaptor.getValue());
        assertArrayEquals(content, contentCaptor.getValue());
        assertEquals(documentId, response.documentId());
        assertEquals(3, response.documentVersion());
    }

    @Test
    @DisplayName("파일 내용과 확장자가 일치하지 않으면 등록하지 않는다")
    void rejectsInvalidFileSignature() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf",
                "not-pdf".getBytes(StandardCharsets.UTF_8));
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetDocumentService.createDocument(
                        assetId, ownerId, "ISSUER",
                        DocumentType.APPRAISAL, file));

        assertEquals(AssetErrorCode.INVALID_ASSET_DOCUMENT, exception.getErrorCode());
        verifyNoInteractions(assetDocumentRepository,
                assetDocumentQueryRepository, s3StorageService);
    }

    @Test
    @DisplayName("파일명에 포함된 경로는 제거하고 원본 파일명만 저장한다")
    void removesPathFromOriginalFilename() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        byte[] content = "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\fakepath\\appraisal.pdf",
                "application/pdf", content);

        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findNextVersion(
                assetId, DocumentType.APPRAISAL))
                .thenReturn(1);
        when(assetDocumentRepository.saveAndFlush(any(AssetDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assetDocumentService.createDocument(
                assetId, ownerId, "ISSUER",
                DocumentType.APPRAISAL, file);

        ArgumentCaptor<AssetDocument> documentCaptor =
                ArgumentCaptor.forClass(AssetDocument.class);
        verify(assetDocumentRepository)
                .saveAndFlush(documentCaptor.capture());
        assertEquals("appraisal.pdf",
                documentCaptor.getValue().getOriginalFilename());
    }

    @Test
    @DisplayName("자산운용자는 다른 사람의 자산에 문서를 등록할 수 없다")
    void rejectsOtherOwnersDocument() {
        UUID assetId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "proof.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(UUID.randomUUID())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetDocumentService.createDocument(
                        assetId, UUID.randomUUID(), "ISSUER",
                        DocumentType.RIGHT_PROOF, file));

        assertEquals(AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetDocumentRepository,
                assetDocumentQueryRepository, s3StorageService);
    }

    @Test
    @DisplayName("자산 문서 목록을 조회하고 다음 커서를 반환한다")
    void getsDocuments() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AssetDocument first = document(assetId, 1, "first.pdf");
        AssetDocument second = document(assetId, 2, "second.pdf");
        AssetDocument extra = document(assetId, 3, "extra.pdf");
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findDocuments(
                assetId, null, 3, Sort.Direction.DESC))
                .thenReturn(List.of(first, second, extra));

        AssetDocumentListResponse response = assetDocumentService.getDocuments(
                assetId, ownerId, "ISSUER", null, 2, Sort.Direction.DESC);

        assertEquals(2, response.documents().size());
        assertTrue(response.hasNext());
        assertEquals(second.getId(), response.nextCursor());
    }

    @Test
    @DisplayName("투자자는 승인되지 않은 자산 문서를 조회할 수 없다")
    void rejectsInvestorReadingDraftDocuments() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset(UUID.randomUUID())));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetDocumentService.getDocuments(
                        assetId, UUID.randomUUID(), "INVESTOR",
                        null, 20, Sort.Direction.DESC));

        assertEquals(AssetErrorCode.ASSET_DOCUMENT_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetDocumentQueryRepository);
    }

    @Test
    @DisplayName("자산 문서의 S3 다운로드 URL을 발급한다")
    void createsDownloadUrl() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AssetDocument document = document(assetId, 1, "appraisal.pdf");
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findActiveById(assetId, document.getId()))
                .thenReturn(Optional.of(document));
        when(s3StorageService.createDownloadUrl(document.getS3ObjectKey()))
                .thenReturn("https://example.com/download");

        AssetDocumentDownloadResponse response = assetDocumentService.createDownloadUrl(
                assetId, document.getId(), ownerId, "ISSUER");

        assertEquals(document.getId(), response.documentId());
        assertEquals("appraisal.pdf", response.originalFilename());
        assertEquals("https://example.com/download", response.downloadUrl());
        assertNotNull(response.expiresAt());
        verify(s3StorageService).createDownloadUrl(document.getS3ObjectKey());
    }

    @Test
    @DisplayName("다른 자산이거나 삭제된 문서는 다운로드 URL을 발급하지 않는다")
    void rejectsMissingDocumentDownload() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(assetQueryRepository.findActiveById(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findActiveById(assetId, documentId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetDocumentService.createDownloadUrl(
                        assetId, documentId, ownerId, "ISSUER"));

        assertEquals(AssetErrorCode.ASSET_DOCUMENT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(s3StorageService);
    }

    @Test
    @DisplayName("자산 문서를 소프트 삭제하고 S3 파일을 제거한다")
    void deletesDocument() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AssetDocument document = document(assetId, 1, "appraisal.pdf");
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findActiveById(assetId, document.getId()))
                .thenReturn(Optional.of(document));

        assetDocumentService.deleteDocument(
                assetId, document.getId(), ownerId, "ISSUER");

        assertTrue(document.isDeleted());
        assertEquals(ownerId, document.getDeletedBy());
        assertNotNull(document.getDeletedAt());
        verify(s3StorageService).delete(document.getS3ObjectKey());
    }

    @Test
    @DisplayName("존재하지 않는 문서는 삭제하지 않는다")
    void rejectsMissingDocumentDeletion() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset(ownerId)));
        when(assetDocumentQueryRepository.findActiveById(assetId, documentId))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetDocumentService.deleteDocument(
                        assetId, documentId, ownerId, "ISSUER"));

        assertEquals(AssetErrorCode.ASSET_DOCUMENT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(s3StorageService);
    }

    private AssetDocument document(
            UUID assetId,
            int version,
            String filename
    ) {
        AssetDocument document = new AssetDocument(
                assetId, DocumentType.APPRAISAL, version,
                filename, "assets/" + assetId + "/documents/" + UUID.randomUUID(),
                "application/pdf", 100L, "a".repeat(64));
        ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(document, "createdAt", Instant.now());
        return document;
    }

    private Asset asset(UUID ownerId) {
        return new Asset(
                ownerId, "테스트 자산", AssetType.REAL_ESTATE, "자산 설명",
                100_000_000L, BigDecimal.ZERO, Map.of(), 10_000L);
    }
}
