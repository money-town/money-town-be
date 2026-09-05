package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * S3 파일 저장소
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    /**
     * 파일 업로드
     */
    public void upload(
            String objectKey,
            byte[] content,
            String contentType
    ) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(content)
            );
        } catch (SdkException exception) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_STORAGE_FAILED
            );
        }
    }

    /**
     * 임시 다운로드 URL 발급
     */
    public String createDownloadUrl(String objectKey) {
        try {
            GetObjectRequest getObjectRequest =
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build();

            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(10))
                            .getObjectRequest(getObjectRequest)
                            .build();

            return s3Presigner.presignGetObject(presignRequest)
                    .url()
                    .toString();
        } catch (SdkException exception) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_STORAGE_FAILED
            );
        }
    }

    /**
     * 파일 삭제
     */
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build();

            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DOCUMENT_STORAGE_FAILED
            );
        }
    }

    /**
     * 파일 업로드 후 DB 롤백 시 S3 파일 제거
     */
    public void uploadWithRollbackCleanup(
            String objectKey,
            byte[] content,
            String contentType
    ) {
        // 현재 DB 트랜잭션이 있으면 롤백 처리 등록
        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != TransactionSynchronization
                                    .STATUS_ROLLED_BACK) {
                                return;
                            }

                            try {
                                delete(objectKey);
                            } catch (RuntimeException exception) {
                                log.error(
                                        "롤백된 S3 파일 정리에 실패했습니다. objectKey={}",
                                        objectKey,
                                        exception
                                );
                            }
                        }
                    }
            );
        }

        // 실제 파일 업로드
        upload(objectKey, content, contentType);
    }

    /**
     * DB 커밋 완료 후 S3 파일 삭제
     */
    public void deleteAfterCommit(String objectKey) {
        // 기존 파일이 없으면 삭제하지 않음
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        // 트랜잭션이 없으면 즉시 삭제
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            delete(objectKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            delete(objectKey);
                        } catch (RuntimeException exception) {
                            log.error(
                                    "DB 커밋 후 S3 파일 삭제에 실패했습니다. objectKey={}",
                                    objectKey,
                                    exception
                            );
                        }
                    }
                }
        );
    }
}