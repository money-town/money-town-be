package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.AssetDocumentCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentDownloadResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDocumentListResponse;
import com.moneykk.moneytown.asset.entity.DocumentType;
import com.moneykk.moneytown.asset.service.AssetDocumentService;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 자산 문서 API
 */
@Validated
@RestController
@RequestMapping("/api/v1/assets/{assetId}/documents")
@RequiredArgsConstructor
public class AssetDocumentController {

    private final AssetDocumentService assetDocumentService;

    /**
     * 자산 문서 등록
     */
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDocumentCreateResponse> createDocument(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam DocumentType documentType,
            @RequestPart("file") MultipartFile file
    ) {
        // 파일 검증 후 S3와 DB에 저장
        AssetDocumentCreateResponse response =
                assetDocumentService.createDocument(
                        assetId,
                        userId,
                        role,
                        documentType,
                        file
                );

        return ApiResponse.success(
                response,
                "자산 문서가 등록되었습니다."
        );
    }

    /**
     * 자산 문서 목록 조회
     */
    @GetMapping
    public ApiResponse<AssetDocumentListResponse> getDocuments(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,

            // 첫 요청에는 커서 생략
            @RequestParam(required = false) UUID cursor,

            // 기본 20건, 최대 100건
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            int size,

            // 기본은 최신 등록순
            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction
    ) {
        AssetDocumentListResponse response =
                assetDocumentService.getDocuments(
                        assetId,
                        userId,
                        role,
                        cursor,
                        size,
                        direction
                );

        return ApiResponse.success(
                response,
                "자산 문서 목록 조회가 완료되었습니다."
        );
    }

    /**
     * 자산 문서 다운로드 URL 발급
     */
    @GetMapping("/{documentId}/download-url")
    public ApiResponse<AssetDocumentDownloadResponse> createDownloadUrl(
            @PathVariable UUID assetId,
            @PathVariable UUID documentId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        AssetDocumentDownloadResponse response =
                assetDocumentService.createDownloadUrl(
                        assetId,
                        documentId,
                        userId,
                        role
                );

        return ApiResponse.success(
                response,
                "자산 문서 다운로드 URL이 발급되었습니다."
        );
    }

    /**
     * 자산 문서 삭제
     */
    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable UUID assetId,
            @PathVariable UUID documentId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        assetDocumentService.deleteDocument(
                assetId,
                documentId,
                userId,
                role
        );

        return ApiResponse.success(
                null,
                "자산 문서가 삭제되었습니다."
        );
    }
}