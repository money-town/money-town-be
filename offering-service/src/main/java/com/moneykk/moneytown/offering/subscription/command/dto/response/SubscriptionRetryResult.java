package com.moneykk.moneytown.offering.subscription.command.dto.response;

/**
 * 청약 재처리 결과와 멱등 재호출 여부.
 */
public record SubscriptionRetryResult(

        SubscriptionRetryResponse response,

        /**
         * 동일한 Idempotency-Key로 완료된 요청을
         * 다시 조회한 경우 true.
         */
        boolean replayed

) {

    /**
     * 최초 청약 재처리 요청 결과.
     */
    public static SubscriptionRetryResult created(
            SubscriptionRetryResponse response
    ) {
        return new SubscriptionRetryResult(
                response,
                false
        );
    }

    /**
     * 기존에 완료된 청약 재처리 결과.
     */
    public static SubscriptionRetryResult replayed(
            SubscriptionRetryResponse response
    ) {
        return new SubscriptionRetryResult(
                response,
                true
        );
    }
}