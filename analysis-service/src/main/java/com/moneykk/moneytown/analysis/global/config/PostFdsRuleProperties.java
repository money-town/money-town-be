package com.moneykk.moneytown.analysis.global.config;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.util.Map;

@ConfigurationProperties(prefix = "fds.post")
public record PostFdsRuleProperties(Map<RuleCode, PostThreshold> rules) {

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
}
