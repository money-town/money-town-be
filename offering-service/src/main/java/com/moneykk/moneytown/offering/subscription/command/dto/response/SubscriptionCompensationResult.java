package com.moneykk.moneytown.offering.subscription.command.dto.response;

/**
 * 청약 보상 요청 결과와 멱등 재호출 여부.
 */
public record SubscriptionCompensationResult(

        SubscriptionCompensationResponse response,

        /**
         * 동일한 Idempotency-Key로 완료된 요청을 다시 조회한 경우 true.
         */
        boolean replayed

) {

    /**
     * 최초로 접수된 청약 보상 요청 결과.
     */
    public static SubscriptionCompensationResult created(
            SubscriptionCompensationResponse response
    ) {
        return new SubscriptionCompensationResult(
                response,
                false
        );
    }

    /**
     * 기존에 완료된 청약 보상 요청 결과.
     */
    public static SubscriptionCompensationResult replayed(
            SubscriptionCompensationResponse response
    ) {
        return new SubscriptionCompensationResult(
                response,
                true
        );
    }
}