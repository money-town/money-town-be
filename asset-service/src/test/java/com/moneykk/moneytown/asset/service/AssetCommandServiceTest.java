package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.request.AssetUpdateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.AssetStatus;
import com.moneykk.moneytown.asset.entity.AssetType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.asset.repository.AssetQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetCommandServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @Mock
    private S3StorageService s3StorageService;

    @InjectMocks
    private AssetCommandService assetCommandService;

    @ParameterizedTest
    @CsvSource({
            "ISSUER, REAL_ESTATE",
            "ISSUER, MUSIC_COPYRIGHT",
            "ADMIN, REAL_ESTATE",
            "ADMIN, MUSIC_COPYRIGHT"
    })
    @DisplayName("운용자와 관리자는 본인 소유 자산을 DRAFT 상태로 등록한다")
    void createsAsset(String role, AssetType type) {
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-03T01:00:00Z");
        AssetCreateRequest request = request(type);
        // DB에서 생성되는 값은 테스트에서 지정
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", assetId);
            ReflectionTestUtils.setField(asset, "createdAt", createdAt);
            return asset;
        });

        AssetCreateResponse response = assetCommandService.createAsset(userId, role, request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(request.assetName(), saved.getAssetName());
        assertEquals(type, saved.getType());
        assertEquals(request.description(), saved.getDescription());
        assertEquals(request.valuationAmount().longValue(), saved.getValuationAmount());
        assertEquals(request.expectedReturnRate(), saved.getExpectedReturnRate());
        assertEquals(request.detailData(), saved.getDetailData());
        assertEquals(request.valuationAmount() / request.totalShareQuantity(), saved.getUnitPrice());
        assertEquals(request.totalShareQuantity().longValue(), saved.getTotalShareQuantity());
        assertEquals(request.valuationAmount() - saved.getUnitPrice() * saved.getTotalShareQuantity(),
                saved.getRoundingDifferenceAmount());
        assertEquals(0L, saved.getAllocatedQuantity());
        assertEquals(AssetStatus.DRAFT, saved.getStatus());
        assertEquals(new AssetCreateResponse(assetId, request.assetName(), AssetStatus.DRAFT, createdAt),
                response);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"INVESTOR", "SYSTEM", "issuer", " ADMIN "})
    @DisplayName("등록 권한이 없으면 자산을 저장하지 않는다")
    void rejectsUnauthorizedRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.createAsset(UUID.randomUUID(), role, request(AssetType.REAL_ESTATE)));

        assertEquals(AssetErrorCode.ASSET_CREATE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetRepository);
    }

    @ParameterizedTest
    @CsvSource({"ISSUER, DRAFT", "ISSUER, REJECTED", "ADMIN, DRAFT", "ADMIN, REJECTED"})
    @DisplayName("등록자와 관리자는 자산을 수정하고 단가와 차액을 재계산한다")
    void updatesAsset(String role, AssetStatus status) {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = "ADMIN".equals(role) ? UUID.randomUUID() : ownerId;
        Asset asset = assetForUpdate(ownerId, status);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
        AssetUpdateRequest update = new AssetUpdateRequest("수정한 자산", null, "예시자산",
                null, 30_000L);

        assetCommandService.updateAsset(assetId, callerId, role, update);

        assertEquals("수정한 자산", asset.getAssetName());
        assertEquals("예시자산", asset.getOwnerName());
        // 생략한 정보와 변경 불가 항목은 유지
        assertEquals("기존 설명", asset.getDescription());
        assertEquals("서울", asset.getDetailData().get("address"));
        assertEquals(ownerId, asset.getUserId());
        assertEquals(AssetType.REAL_ESTATE, asset.getType());
        assertEquals(status, asset.getStatus());
        assertEquals(100_000_000L, asset.getValuationAmount());
        assertEquals(30_000L, asset.getTotalShareQuantity());
        assertEquals(3_333L, asset.getUnitPrice());
        assertEquals(10_000L, asset.getRoundingDifferenceAmount());
        assertEquals(100_000_000L, asset.getDetailData().get("appraisalAmount"));
        verify(assetQueryRepository).findActiveByIdForUpdate(assetId);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"INVESTOR", "SYSTEM", "issuer", " ADMIN "})
    @DisplayName("수정 권한이 없으면 저장소를 조회하지 않는다")
    void rejectsUnauthorizedUpdateRole(String role) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(UUID.randomUUID(), UUID.randomUUID(), role,
                        new AssetUpdateRequest("수정한 자산", null, null, null, null)));
        assertEquals(AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetRepository, assetQueryRepository);
    }

    @Test
    @DisplayName("사용자 ID가 없으면 관리자도 수정할 수 없다")
    void rejectsMissingUpdateUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(UUID.randomUUID(), null, "ADMIN",
                        new AssetUpdateRequest("수정한 자산", null, null, null, null)));
        assertEquals(AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(assetRepository, assetQueryRepository);
    }

    @Test
    @DisplayName("운용자는 다른 사람이 등록한 자산을 수정할 수 없다")
    void rejectsOtherOwnersAsset() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, UUID.randomUUID(), "ISSUER",
                        new AssetUpdateRequest("수정한 자산", null, null, null, null)));

        assertEquals(AssetErrorCode.ASSET_UPDATE_ACCESS_DENIED, exception.getErrorCode());
        assertEquals("기존 자산", asset.getAssetName());
    }

    @Test
    @DisplayName("조회 가능한 자산이 없으면 미존재 오류를 반환한다")
    void rejectsMissingAssetForUpdate() {
        UUID assetId = UUID.randomUUID();
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.empty());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, UUID.randomUUID(), "ADMIN",
                        new AssetUpdateRequest("수정한 자산", null, null, null, null)));
        assertEquals(AssetErrorCode.ASSET_NOT_FOUND, exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = AssetStatus.class,
            names = {"REVIEW_REQUESTED", "APPROVED", "SUSPENDED", "TERMINATED"})
    @DisplayName("작성 중 또는 반려 상태가 아니면 관리자도 수정할 수 없다")
    void rejectsNonEditableStatus(AssetStatus status) {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, status);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, ownerId, "ADMIN",
                        new AssetUpdateRequest("수정한 자산", null, null, null, null)));

        assertEquals(AssetErrorCode.ASSET_UPDATE_NOT_ALLOWED, exception.getErrorCode());
        assertEquals("기존 자산", asset.getAssetName());
    }

    @ParameterizedTest
    @CsvSource({
            "100000000, 30000, 3333, 10000",
            "100000000, 100000000, 1, 0",
            "100000000, 3, 33333333, 1",
            "9223372036854775807, 3, 3074457345618258602, 1"
    })
    @DisplayName("수량 수정 시 기존 평가금액을 유지하고 단가와 차액을 재계산한다")
    void recalculatesPartialPrice(long originalAmount, long quantity,
                                 long expectedPrice, long expectedDifference) {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = new Asset(ownerId, "기존 자산", AssetType.REAL_ESTATE, "기존 설명",
                originalAmount, new BigDecimal("5.2500"),
                Map.of("appraisalAmount", originalAmount), 10_000L);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.updateAsset(assetId, ownerId, "ISSUER",
                new AssetUpdateRequest(null, null, null, null, quantity));

        assertEquals(originalAmount, asset.getValuationAmount());
        assertEquals(originalAmount, asset.getDetailData().get("appraisalAmount"));
        assertEquals(quantity, asset.getTotalShareQuantity());
        assertEquals(expectedPrice, asset.getUnitPrice());
        assertEquals(expectedDifference, asset.getRoundingDifferenceAmount());
        assertEquals("기존 자산", asset.getAssetName());
    }

    @Test
    @DisplayName("설명과 주소만 수정하면 지분 가격 정보와 나머지 상세 정보는 유지된다")
    void preservesPriceWhenOnlyDetailsChange() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.updateAsset(assetId, ownerId, "ISSUER",
                new AssetUpdateRequest(null, "새 설명", null, Map.of("address", "부산"), null));

        assertEquals("새 설명", asset.getDescription());
        assertEquals("부산", asset.getDetailData().get("address"));
        assertEquals(100_000_000L, asset.getDetailData().get("appraisalAmount"));
        assertEquals(100_000_000L, asset.getValuationAmount());
        assertEquals(10_000L, asset.getTotalShareQuantity());
        assertEquals(10_000L, asset.getUnitPrice());
        assertEquals(0L, asset.getRoundingDifferenceAmount());
    }

    @ParameterizedTest
    @MethodSource("appraisalAmountUpdateRequests")
    @DisplayName("평가금액 요청은 값과 권한에 관계없이 거부하고 기존 정보를 유지한다")
    void rejectsAppraisalAmountUpdate(Object amount, String role) {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));
        // null도 명시적으로 전달하는 경우를 검증
        Map<String, Object> detail = new HashMap<>();
        detail.put("appraisalAmount", amount);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, ownerId, role,
                        new AssetUpdateRequest("변경되면 안 됨", null, null, detail, 30_000L)));

        assertEquals(AssetErrorCode.APPRAISAL_AMOUNT_UPDATE_NOT_ALLOWED, exception.getErrorCode());
        assertEquals("기존 자산", asset.getAssetName());
        assertEquals(100_000_000L, asset.getValuationAmount());
        assertEquals(100_000_000L, asset.getDetailData().get("appraisalAmount"));
        assertEquals(10_000L, asset.getTotalShareQuantity());
        assertEquals(10_000L, asset.getUnitPrice());
        assertEquals(0L, asset.getRoundingDifferenceAmount());
    }

    static Stream<Arguments> appraisalAmountUpdateRequests() {
        return Stream.of(null, 100_000_000L, 120_000_000L, "100000000", true, 100000000.5d, 100000000.5f,
                        new BigDecimal("100000000.5"), new BigInteger("9223372036854775808"),
                        0L, -1L, 9999L)
                .flatMap(value -> Stream.of("ISSUER", "ADMIN").map(role -> Arguments.of(value, role)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 100000001})
    @DisplayName("수량이 양수가 아니거나 단가가 1원 미만이면 수정하지 않는다")
    void rejectsInvalidUpdateQuantity(long quantity) {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, ownerId, "ISSUER",
                        new AssetUpdateRequest("변경되면 안 됨", null, null, null, quantity)));

        assertEquals(AssetErrorCode.INVALID_ASSET_SHARE_PRICE, exception.getErrorCode());
        assertEquals("기존 자산", asset.getAssetName());
        assertEquals(10_000L, asset.getTotalShareQuantity());
    }

    @Test
    @DisplayName("자산운용자는 본인 자산의 심사를 요청한다")
    void issuerRequestsAssetReview() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.changeAssetStatus(
                assetId, ownerId, "ISSUER", AssetStatus.REVIEW_REQUESTED, null);

        assertEquals(AssetStatus.REVIEW_REQUESTED, asset.getStatus());
        assertEquals(null, asset.getRejectionReason());
    }

    @Test
    @DisplayName("관리자는 심사 요청된 자산을 사유와 함께 반려한다")
    void adminRejectsAssetWithReason() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.REVIEW_REQUESTED);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.changeAssetStatus(
                assetId, UUID.randomUUID(), "ADMIN", AssetStatus.REJECTED, " 서류 보완 필요 ");

        assertEquals(AssetStatus.REJECTED, asset.getStatus());
        assertEquals("서류 보완 필요", asset.getRejectionReason());
    }

    @Test
    @DisplayName("관리자가 반려 사유를 입력하지 않으면 상태를 변경하지 않는다")
    void rejectsMissingRejectionReason() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.REVIEW_REQUESTED);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.changeAssetStatus(
                        assetId, UUID.randomUUID(), "ADMIN", AssetStatus.REJECTED, " "));

        assertEquals(AssetErrorCode.ASSET_REJECTION_REASON_REQUIRED, exception.getErrorCode());
        assertEquals(AssetStatus.REVIEW_REQUESTED, asset.getStatus());
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 거부한다")
    void rejectsInvalidStatusTransition() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.changeAssetStatus(
                        assetId, UUID.randomUUID(), "ADMIN", AssetStatus.APPROVED, null));

        assertEquals(AssetErrorCode.INVALID_ASSET_STATUS_TRANSITION, exception.getErrorCode());
        assertEquals(AssetStatus.DRAFT, asset.getStatus());
    }

    @Test
    @DisplayName("자산운용자는 다른 사람의 자산 상태를 변경할 수 없다")
    void rejectsOtherIssuersStatusChange() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.changeAssetStatus(
                        assetId, UUID.randomUUID(), "ISSUER", AssetStatus.REVIEW_REQUESTED, null));

        assertEquals(AssetErrorCode.ASSET_STATUS_CHANGE_ACCESS_DENIED, exception.getErrorCode());
        assertEquals(AssetStatus.DRAFT, asset.getStatus());
    }

    @Test
    @DisplayName("자산운용자는 본인이 등록한 작성 중 자산을 삭제한다")
    void issuerDeletesOwnDraftAsset() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.deleteAsset(assetId, ownerId, "ISSUER");

        assertTrue(asset.isDeleted());
        assertEquals(ownerId, asset.getDeletedBy());
        assertNotNull(asset.getDeletedAt());
    }

    @Test
    @DisplayName("관리자는 반려된 자산을 삭제한다")
    void adminDeletesRejectedAsset() {
        UUID assetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.REJECTED);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        assetCommandService.deleteAsset(assetId, adminId, "ADMIN");

        assertTrue(asset.isDeleted());
        assertEquals(adminId, asset.getDeletedBy());
    }

    @Test
    @DisplayName("자산운용자는 다른 사람이 등록한 자산을 삭제할 수 없다")
    void rejectsOtherOwnersAssetDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.DRAFT);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.deleteAsset(assetId, UUID.randomUUID(), "ISSUER"));

        assertEquals(AssetErrorCode.ASSET_DELETE_ACCESS_DENIED, exception.getErrorCode());
        assertFalse(asset.isDeleted());
    }

    @Test
    @DisplayName("승인된 자산은 관리자도 삭제할 수 없다")
    void rejectsApprovedAssetDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = assetForUpdate(UUID.randomUUID(), AssetStatus.APPROVED);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.deleteAsset(assetId, UUID.randomUUID(), "ADMIN"));

        assertEquals(AssetErrorCode.ASSET_DELETE_NOT_ALLOWED, exception.getErrorCode());
        assertFalse(asset.isDeleted());
    }

    @Test
    @DisplayName("전체 지분 수량을 이미 배정된 수량보다 줄일 수 없다")
    void rejectsQuantityBelowAllocatedShares() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.REJECTED);
        ReflectionTestUtils.setField(asset, "allocatedQuantity", 100L);
        when(assetQueryRepository.findActiveByIdForUpdate(assetId)).thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.updateAsset(assetId, ownerId, "ADMIN",
                        new AssetUpdateRequest(null, null, null, null, 99L)));

        assertEquals(AssetErrorCode.INVALID_HOLDING_QUANTITY, exception.getErrorCode());
        assertEquals(10_000L, asset.getTotalShareQuantity());
        assertEquals(100L, asset.getAllocatedQuantity());
    }

    @Test
    @DisplayName("대표 이미지를 교체하고 기존 이미지는 커밋 후 삭제한다")
    void replacesRepresentativeImage() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        asset.updateRepresentativeImage("assets/old-image");
        MockMultipartFile file = new MockMultipartFile(
                "file", "asset.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset));

        assetCommandService.setRepresentativeImage(
                assetId, ownerId, "ISSUER", file);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3StorageService).uploadWithRollbackCleanup(
                keyCaptor.capture(), any(byte[].class), eq("image/png"));
        String newKey = keyCaptor.getValue();
        assertTrue(newKey.startsWith(
                "assets/" + assetId + "/representative/"));
        assertEquals(newKey, asset.getRepresentativeImageKey());
        verify(s3StorageService).deleteAfterCommit("assets/old-image");
    }

    @Test
    @DisplayName("이미지 확장자를 속인 파일은 대표 이미지로 등록할 수 없다")
    void rejectsInvalidRepresentativeImage() {
        UUID assetId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Asset asset = assetForUpdate(ownerId, AssetStatus.DRAFT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "asset.png", "image/png", "not-image".getBytes()
        );
        when(assetQueryRepository.findActiveByIdForUpdate(assetId))
                .thenReturn(Optional.of(asset));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> assetCommandService.setRepresentativeImage(
                        assetId, ownerId, "ISSUER", file));

        assertEquals(AssetErrorCode.INVALID_ASSET_IMAGE, exception.getErrorCode());
        verifyNoInteractions(s3StorageService);
    }

    private Asset assetForUpdate(UUID ownerId, AssetStatus status) {
        Asset asset = new Asset(ownerId, "기존 자산", AssetType.REAL_ESTATE, "기존 설명",
                100_000_000L, new BigDecimal("5.2500"),
                Map.of("address", "서울", "appraisalAmount", 100_000_000L), 10_000L);
        // 테스트할 상태 지정
        ReflectionTestUtils.setField(asset, "status", status);
        return asset;
    }

    private AssetCreateRequest request(AssetType type) {
        return new AssetCreateRequest(
                "테스트 자산", type, "자산 설명", 100_000_000L,
                new BigDecimal("5.2500"), Map.of("description", "상세 정보"),
                10_000L
        );
    }
}
