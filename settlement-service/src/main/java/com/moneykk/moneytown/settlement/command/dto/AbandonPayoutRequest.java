package com.moneykk.moneytown.settlement.command.dto;

import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AbandonPayoutRequest(

        @Schema(description = "관리자가 실제로 지급을 완료한 방법. OTHER는 resolutionNote가 추가로 필수다.")
        @NotNull(message = "정산 방식(resolutionType)은 필수입니다.")
        ResolutionType resolutionType,

        @Schema(description = "지급 증빙 번호 — BANK_TRANSFER는 은행 이체 확인번호, "
                + "OTHER는 그 경우에 맞는 참조 번호. 이 API는 관리자가 이미 다른 방법으로 실제 지급을 완료했다는 전제로 호출한다 "
                + "— 이 필드가 그 증빙이며, 지급 없이 이 API만 호출해서는 안 된다.")
        @NotBlank(message = "지급 증빙 번호(resolutionReference)는 필수입니다.")
        @Size(max = 200, message = "지급 증빙 번호는 200자를 넘을 수 없습니다.")
        String resolutionReference,

        @Schema(description = "상세 설명. resolutionType이 OTHER면 필수(감사 근거를 남기기 위함), 그 외에는 선택.")
        @Size(max = 500, message = "상세 설명은 500자를 넘을 수 없습니다.")
        String resolutionNote

) {
}