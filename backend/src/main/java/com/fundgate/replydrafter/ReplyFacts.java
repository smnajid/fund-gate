package com.fundgate.replydrafter;

import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import com.fundgate.shareclasses.Isin;
import java.util.List;
import java.util.Optional;

/**
 * One checked request as the Reply Drafter sees it: the Portfolio Id, never the reference, and every rule outcome
 * with its Fund Documentation Citations only; Client Profile values stay in the Check Record (ADR 0004).
 */
public record ReplyFacts(PortfolioId portfolioId, Isin shareClass, Optional<Money> amount, Verdict verdict,
        List<RuleOutcome> outcomes, List<String> notices) {

    public ReplyFacts {
        outcomes = List.copyOf(outcomes);
        notices = List.copyOf(notices);
    }

    public static ReplyFacts of(SubscriptionRequest request, EligibilityResult result) {
        return new ReplyFacts(request.portfolioId(), request.shareClass(), request.amount(), result.verdict(),
                result.justification().outcomes().stream().map(ReplyFacts::withoutClientProfileValues).toList(),
                result.justification().notices());
    }

    /**
     * Only passages of the Fund Documentation pass, an allow-list: a Fund Registration citation names the client's
     * country, and the engine's reasons quote client facts ("a retail client", "not registered in US"), so the
     * reason is left out too.
     */
    private static RuleOutcome withoutClientProfileValues(RuleOutcome outcome) {
        return new RuleOutcome(outcome.rule(), outcome.outcome(), "", outcome.citations().stream()
                .filter(Citation.Document.class::isInstance)
                .toList());
    }
}
