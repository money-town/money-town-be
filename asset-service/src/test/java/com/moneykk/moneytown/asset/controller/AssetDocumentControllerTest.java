package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.AssetDocumentCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentDownloadResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListItemResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListResponse;
import com.moneykk.moneytown.asset.entity.DocumentType;
import com.moneykk.moneytown.asset.service.AssetDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetDocumentControllerTest {

    private final UUID assetId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private AssetDocumentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AssetDocumentService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AssetDocumentController(service))
                .build();
    }

    @Test
    @DisplayName("multipart 자산 문서 등록 요청을 서비스에 전달한다")
    void createsDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "appraisal.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8));
        AssetDocumentCreateResponse response = new AssetDocumentCreateResponse(
                UUID.randomUUID(), assetId, DocumentType.APPRAISAL,
                1, "appraisal.pdf", file.getSize(),
                Instant.parse("2026-09-04T01:00:00Z"));
        when(service.createDocument(
                assetId, userId, "ISSUER", DocumentType.APPRAISAL, file))
                .thenReturn(response);

        mvc.perform(multipart("/api/v1/assets/{assetId}/documents", assetId)
                        .file(file)
                        .param("documentType", "APPRAISAL")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "ISSUER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("자산 문서가 등록되었습니다."))
                .andExpect(jsonPath("$.data.documentId").value(response.documentId().toString()))
                .andExpect(jsonPath("$.data.documentVersion").value(1));

        verify(service).createDocument(
                assetId, userId, "ISSUER", DocumentType.APPRAISAL, file);
    }

    @Test
    @DisplayName("문서 목록은 기본 등록일 내림차순으로 조회한다")
    void getsDocumentsWithDefaultSort() throws Exception {
        UUID documentId = UUID.randomUUID();
        AssetDocumentListResponse response = new AssetDocumentListResponse(
                List.of(new AssetDocumentListItemResponse(
                        documentId, DocumentType.APPRAISAL, 1,
                        "appraisal.pdf", "application/pdf", 100L,
                        Instant.parse("2026-09-04T01:00:00Z"))),
                null,
                false
        );
        when(service.getDocuments(
                assetId, userId, "INVESTOR", null, 20, Sort.Direction.DESC))
                .thenReturn(response);

        mvc.perform(get("/api/v1/assets/{assetId}/documents", assetId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "INVESTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents[0].documentId")
                        .value(documentId.toString()))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(service).getDocuments(
                assetId, userId, "INVESTOR", null, 20, Sort.Direction.DESC);
    }

    @Test
    @DisplayName("문서 다운로드 URL 발급 결과를 반환한다")
    void createsDownloadUrl() throws Exception {
        UUID documentId = UUID.randomUUID();
        AssetDocumentDownloadResponse response = new AssetDocumentDownloadResponse(
                documentId,
                "appraisal.pdf",
                "https://example.com/download",
                Instant.parse("2026-09-04T01:10:00Z")
        );
        when(service.createDownloadUrl(
                assetId, documentId, userId, "INVESTOR"))
                .thenReturn(response);

        mvc.perform(get(
                        "/api/v1/assets/{assetId}/documents/{documentId}/download-url",
                        assetId,
                        documentId)
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "INVESTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId")
                        .value(documentId.toString()))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("https://example.com/download"));

        verify(service).createDownloadUrl(
                assetId, documentId, userId, "INVESTOR");
    }
}
