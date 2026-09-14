package com.moneykk.moneytown.analysis.global.config;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.FailureSource;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "fds.post")
public record PostFdsRuleProperties(Map<RuleCode, PostThreshold> rules,
                                    Map<FailureSource, Set<String>> aggregation) {

    public record PostThreshold(int windowSeconds, int threshold, Integer sampleSize){
        public PostThreshold{
            if (sampleSize == null){
                sampleSize = 10;
            }
        }
    }

    public PostThreshold get(RuleCode code){
        return rules.get(code);
    }

    public boolean isAggregationTarget(FailureSource source, String reasonCode) {
        if (source == null || reasonCode == null) return false;
        return aggregation.getOrDefault(source, Set.of()).contains(reasonCode);
    }
}
