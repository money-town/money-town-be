package com.moneykk.moneytown.offering.subscription.command.application;

import lombok.Getter;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class SubscriptionCompensationRecoveryBatchException
        extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID offeringId;
    private final List<UUID> subscriptionIds;

    public SubscriptionCompensationRecoveryBatchException(
            UUID offeringId,
            List<UUID> subscriptionIds,
            Throwable cause
    ) {
        super(
                "장기 미완료 보상 복구 배치 처리에 실패했습니다. "
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
