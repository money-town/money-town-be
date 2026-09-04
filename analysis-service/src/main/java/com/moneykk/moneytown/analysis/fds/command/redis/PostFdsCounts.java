package com.moneykk.moneytown.analysis.fds.command.redis;

public record PostFdsCounts(long failCount, long limitExceededCount, long recentSize, long cancelledCount) {
}
