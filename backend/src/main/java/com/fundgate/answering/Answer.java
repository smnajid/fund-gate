package com.fundgate.answering;

import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.replydrafter.DraftedReply;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** What the Fund Dealer gets back for one email: its answer, or the Privacy Gate's refusal. */
public sealed interface Answer {

    /**
     * The Privacy Gate's swaps, one Eligibility Check per portfolio and Share Class the email names, and the reply,
     * which names portfolios by Portfolio Reference.
     */
    record Answered(List<Swap> swaps, List<Check> checks, DraftedReply reply) implements Answer {

        public Answered {
            swaps = List.copyOf(swaps);
            checks = List.copyOf(checks);
        }
    }

    /** No LLM call was made and no check run: the Fund Dealer enters the request by hand (ADR 0004). */
    record Refused(String reason, List<String> found) implements Answer {

        public Refused {
            found = List.copyOf(found);
        }
    }

    /** One Portfolio Reference and the Portfolio Id the LLM saw in its place. */
    record Swap(PortfolioReference portfolioRef, PortfolioId portfolioId) {
    }

    /** The checkId names its Check Record. */
    record Check(UUID checkId, PortfolioReference portfolioRef, SubscriptionRequest request, EligibilityResult result) {

        public Check {
            Objects.requireNonNull(checkId, "checkId");
            Objects.requireNonNull(portfolioRef, "Portfolio Reference");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(result, "result");
        }
    }
}
