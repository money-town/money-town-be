package com.moneykk.moneytown.offering.offering.command.application;

import lombok.Getter;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class OfferingCancellationBatchException
        extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID offeringId;
    private final List<UUID> subscriptionIds;

    public OfferingCancellationBatchException(
            UUID offeringId,
            List<UUID> subscriptionIds,
            Throwable cause
    ) {
        super(
                "관리자 공모 중단 보상 배치 처리에 실패했습니다. "
                        + "offeringId=" + offeringId
                        + ", batchSize=" + subscriptionIds.size(),
                cause
        );

        this.offeringId = Objects.requireNonNull(
                offeringId,
                "offeringId는 필수입니다."
        );

        this.subscriptionIds = List.copyOf(
                Objects.requireNonNull(
                        subscriptionIds,
                        "subscriptionIds는 필수입니다."
                )
        );
    }
}