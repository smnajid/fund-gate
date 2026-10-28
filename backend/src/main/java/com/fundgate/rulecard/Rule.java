package com.fundgate.rulecard;

import com.fundgate.citation.Citation;
import com.fundgate.shareclasses.Isin;
import java.time.Instant;
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
        String summary,
        /** The extractor's confidence; the validation screen shows the lowest first. */
        double confidence,
        /** Two documents disagree on this rule. */
        boolean contradiction,
        /** The LLM's draft first, then every validator action, each with who and when. Never edited, only appended. */
        List<Revision> history) {

    public Rule {
        citations = List.copyOf(citations);
        history = List.copyOf(history);
    }

    public enum Scope { FUND, SUB_FUND, SHARE_CLASS }

    /** One step in a rule's life, and the condition it left the rule with. */
    public record Revision(Action action, String by, Instant at, Condition condition) {

        public enum Action { DRAFTED, ACCEPTED, EDITED, REJECTED, ADDED_BY_VALIDATOR, CONTRADICTION_RESOLVED }
    }

    /** A card can't be validated while any rule is still only drafted. */
    public boolean reviewed() {
        return history.stream().anyMatch(revision -> revision.action() != Revision.Action.DRAFTED);
    }

    /** A Contradiction gives Review until a validator resolves it. */
    public boolean contradictionResolved() {
        return history.stream().anyMatch(revision -> revision.action() == Revision.Action.CONTRADICTION_RESOLVED);
    }
}
