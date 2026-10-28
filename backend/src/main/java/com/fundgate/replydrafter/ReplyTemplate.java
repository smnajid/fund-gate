package com.fundgate.replydrafter;

import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.eligibility.Verdict;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The fixed reply, one line per check, used when no LLM may or can draft it (ADR 0004). */
public final class ReplyTemplate {

    private ReplyTemplate() {
    }

    /** Each portfolio is named as {@code name} gives it: by Portfolio Id before the swap back, else by reference. */
    public static String write(List<CheckedRequest> checks, Function<PortfolioId, String> name) {
        return checks.stream()
                .map(check -> "%s on %s: %s.".formatted(name.apply(check.request().portfolioId()),
                        check.request().shareClass(), phrase(check.result().verdict())))
                .collect(Collectors.joining("\n", "Hello,\n\n", "\n\nFund dealing desk"));
    }

    private static String phrase(Verdict verdict) {
        return switch (verdict) {
            case ELIGIBLE -> "can subscribe";
            case NOT_ELIGIBLE -> "cannot subscribe";
            case NEEDS_REVIEW -> "needs review before we can answer";
        };
    }
}
