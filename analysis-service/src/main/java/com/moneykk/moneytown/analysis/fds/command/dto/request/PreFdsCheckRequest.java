package com.moneykk.moneytown.analysis.fds.command.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreFdsCheckRequest(
        @NotNull UUID requestId,
        @NotNull UUID userId,
        @NotNull UUID assetId
) {}
