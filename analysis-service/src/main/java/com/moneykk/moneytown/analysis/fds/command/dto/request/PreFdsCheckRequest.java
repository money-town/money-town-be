package com.moneykk.moneytown.analysis.fds.command.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreFdsCheckRequest(
        @Schema(description = "요청 추적 ID (멱등 키, 필수)")
        @NotNull UUID requestId,
        @Schema(description = "검사 대상 사용자 ID (필수)")
        @NotNull UUID userId,
        @Schema(description = "검사 대상 자산 ID (필수)")
        @NotNull UUID assetId
) {}
