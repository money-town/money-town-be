package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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

/** S3 파일 저장소 */
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    /** 파일 업로드 */
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

    /** 임시 다운로드 URL 발급 */
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

    /** 파일 삭제 */
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
}