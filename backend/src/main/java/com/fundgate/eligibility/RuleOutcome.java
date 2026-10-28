package com.fundgate.eligibility;

import com.fundgate.citation.Citation;
import com.fundgate.rulecard.RuleKind;
import java.util.List;

/** The outcome of one rule, with what it relies on. A Warning never changes the Verdict. */
public record RuleOutcome(RuleKind rule, Outcome outcome, String reason, List<Citation> citations) {

    public RuleOutcome {
        citations = List.copyOf(citations);
    }

    public enum Outcome { PASS, FAIL, REVIEW, WARNING }
}
