package com.moneykk.moneytown.analysis.global.config;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import com.moneykk.moneytown.analysis.fds.domain.UserStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "fds.pre")
public record FdsRuleProperties(Map<RuleCode, Threshold> rules) {

    public record Threshold(int windowSeconds, int normal, int suspicious){
        public int forStatus(UserStatus status){
            return status == UserStatus.SUSPICIOUS ? suspicious : normal;
        }

    }
    public Threshold get(RuleCode code){
        return rules.get(code);
    }
}
