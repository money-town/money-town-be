package com.moneykk.moneytown.analysis.fds.command.dto.response;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;

public record PreFdsCheckResult(
        String result,
        RuleCode ruleCode
) {

    public static PreFdsCheckResult pass() { return new PreFdsCheckResult("PASS", null); }
    public static PreFdsCheckResult block(RuleCode rule) { return new PreFdsCheckResult("BLOCK", rule); }
    public static PreFdsCheckResult alreadyBlocked() { return new PreFdsCheckResult("BLOCK", null); }
}
