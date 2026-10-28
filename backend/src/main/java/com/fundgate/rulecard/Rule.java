package com.fundgate.rulecard;

import com.fundgate.citation.Citation;
import com.fundgate.shareclasses.Isin;
import java.util.List;
import java.util.Optional;

/**
 * One rule of a Rule Card. A fund-level rule applies to every class; a class-level rule overrides a fund-level rule
 * of the same kind. A Contradiction between documents gives Review until a validator resolves it.
 */
public record Rule(
        Scope scope,
        /** Empty for a fund-level rule. */
        Optional<Isin> shareClass,
        Condition condition,
        List<Citation.Document> citations,
        boolean contradiction,
        String summary,
        Review review) {

    public Rule {
        citations = List.copyOf(citations);
    }

    public enum Scope { FUND, SUB_FUND, SHARE_CLASS }

    /** What the Rule Card Validator did with the drafted rule. A card can't be validated while a rule is unreviewed. */
    public enum Review { UNREVIEWED, ACCEPTED, EDITED, REJECTED, ADDED_BY_VALIDATOR }
}
