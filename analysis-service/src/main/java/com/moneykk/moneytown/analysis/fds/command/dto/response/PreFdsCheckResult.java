package com.moneykk.moneytown.analysis.fds.command.dto.response;

import com.moneykk.moneytown.analysis.fds.domain.RuleCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreFdsCheckResult(
        @Schema(description = "검사 결과", example = "PASS", allowableValues = {"PASS", "BLOCK"})
        String result,
        @Schema(description = "차단 시 적용된 규칙 코드 (PASS이거나 이미 차단된 상태면 null)")
        RuleCode ruleCode
) {

    public static PreFdsCheckResult pass() { return new PreFdsCheckResult("PASS", null); }
    public static PreFdsCheckResult block(RuleCode rule) { return new PreFdsCheckResult("BLOCK", rule); }
    public static PreFdsCheckResult alreadyBlocked() { return new PreFdsCheckResult("BLOCK", null); }
}
