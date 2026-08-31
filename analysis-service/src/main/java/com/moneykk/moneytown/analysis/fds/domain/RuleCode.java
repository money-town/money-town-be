package com.moneykk.moneytown.analysis.fds.domain;

public enum RuleCode {

    // Pre-Fds - 탐지 즉시 BLOCKED
    RAPID_REQUEST(DetectionType.PRE),
    BURST_REQUEST(DetectionType.PRE),
    MULTI_OFFERING_BURST(DetectionType.PRE),

    // Post-Fds - 단계적 상향 (NORMAL-SUSPICIOUS, SUSPICIOUS-BLOCKED)
    REPEATED_FAILURE(DetectionType.POST),
    REPEATED_LIMIT_EXCEEDED(DetectionType.POST),
    HIGH_CANCEL_RATE(DetectionType.POST);

    private final DetectionType detectionType;

    RuleCode(DetectionType detectionType){
        this. detectionType = detectionType;
    }

    public DetectionType getDetectionType(){
        return detectionType;
    }

    public boolean isImmediateBlock(){
        return detectionType == DetectionType.PRE;
    }
}
