package com.fundgate.eligibility;

import java.util.List;

/**
 * Why a Verdict was given: one outcome per rule, each cited, and the notices that apply to the whole check,
 * e.g. "Rule Card not validated", "Fund not covered", "regime not covered" or the warning "Stale Rule Card".
 */
public record Justification(List<RuleOutcome> outcomes, List<String> notices) {

    public Justification {
        outcomes = List.copyOf(outcomes);
        notices = List.copyOf(notices);
    }
}
