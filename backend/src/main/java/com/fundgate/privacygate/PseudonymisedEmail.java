package com.fundgate.privacygate;

import com.fundgate.clientprofiles.PortfolioId;
import java.util.List;
import java.util.Objects;

/**
 * What the modules that call the LLM may see of a gated email: its text with Portfolio Ids, and those Ids in the order
 * the email names them. No Portfolio Reference (ADR 0004).
 */
public record PseudonymisedEmail(String text, List<PortfolioId> portfolioIds) {

    public PseudonymisedEmail {
        Objects.requireNonNull(text, "text");
        portfolioIds = List.copyOf(portfolioIds);
    }
}
