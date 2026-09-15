package com.moneykk.moneytown.offering.offering.command.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record OfferingUpdateRequest(
        @Schema(
                description = "변경할 공모 상품명. 미입력 시 기존 값을 유지합니다.",
                example = "강남 오피스텔 조각투자 1차 공모 변경"
        )
        @Size(max = 200, message = "공모 상품명은 200자를 초과할 수 없습니다.")
        @Pattern(
                regexp = ".*\\S.*",
                message = "공모 상품명은 공백으로만 구성할 수 없습니다."
        )
        String title,

        @Schema(
                description = "변경할 공모 총 모집 수량. 미입력 시 기존 값을 유지합니다.",
                example = "12000"
        )
        @Min(value = 1, message = "총 모집 수량은 1 이상이어야 합니다.")
        Long totalQuantity,

        @Schema(
                description = "변경할 최소 청약 수량. 미입력 시 기존 값을 유지합니다.",
                example = "10"
        )
        @Min(value = 1, message = "최소 청약 수량은 1 이상이어야 합니다.")
        Long minSubscriptionQuantity,

        @Schema(
                description = "변경할 최대 청약 수량. 미입력 시 기존 값을 유지합니다.",
                example = "1000"
        )
        @Min(value = 1, message = "최대 청약 수량은 1 이상이어야 합니다.")
        Long maxSubscriptionQuantity,

        @Schema(
                description = """
                변경할 공모 모집 시작 시각. 미입력 시 기존 값을 유지합니다.
                한국 시각 기준으로 yyyy-MM-dd HH:mm 형식으로 입력합니다.
                현재 시각보다 충분히 미래로 설정해야 합니다.
                """,
                type = "string",
                example = "2030-01-01 14:40"
        )
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        LocalDateTime startAt,

        @Schema(
                description = """
                변경할 공모 모집 종료 시각. 미입력 시 기존 값을 유지합니다.
                한국 시각 기준으로 yyyy-MM-dd HH:mm 형식으로 입력하며
                모집 시작 시각보다 이후여야 합니다.
                """,
                type = "string",
                example = "2030-01-08 18:00"
        )
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        LocalDateTime endAt
) {
}