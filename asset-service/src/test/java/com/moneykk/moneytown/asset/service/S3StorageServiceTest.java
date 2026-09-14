package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

    private S3Client s3Client;
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3StorageService = new S3StorageService(
                s3Client,
                mock(S3Presigner.class)
        );
        ReflectionTestUtils.setField(
                s3StorageService,
                "bucket",
                "test-bucket"
        );
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadFailureDoesNotRegisterRollbackCleanup() {
        TransactionSynchronizationManager.initSynchronization();
        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenThrow(SdkClientException.create("upload failed"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> s3StorageService.uploadWithRollbackCleanup(
                        "assets/test.png",
                        new byte[]{1},
                        "image/png"
                )
        );

        assertEquals(
                AssetErrorCode.ASSET_DOCUMENT_STORAGE_FAILED,
                exception.getErrorCode()
        );
        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
        verify(s3Client, never())
                .deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void successfulUploadIsDeletedAfterTransactionRollback() {
        TransactionSynchronizationManager.initSynchronization();

        s3StorageService.uploadWithRollbackCleanup(
                "assets/test.png",
                new byte[]{1},
                "image/png"
        );

        assertEquals(
                1,
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .size()
        );
        TransactionSynchronizationManager
                .getSynchronizations()
                .get(0)
                .afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );

        verify(s3Client).deleteObject(argThat(request ->
                "test-bucket".equals(request.bucket())
                        && "assets/test.png".equals(request.key())
        ));
    }
}
