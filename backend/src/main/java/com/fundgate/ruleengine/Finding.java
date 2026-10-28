package com.fundgate.ruleengine;

import com.fundgate.citation.Citation;
import com.fundgate.eligibility.RuleOutcome.Outcome;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** What one rule, or one fact a rule reads, gives, with the Client Profile fields and bank data it relied on. */
record Finding(Outcome outcome, String reason, List<Citation> citations) {

    /** Fail is worse than Review, Review than Warning, Warning than Pass. */
    static final Comparator<Outcome> SEVERITY = Comparator.comparingInt(outcome -> switch (outcome) {
        case PASS -> 0;
        case WARNING -> 1;
        case REVIEW -> 2;
        case FAIL -> 3;
    });

    Finding {
        citations = List.copyOf(citations);
    }

    static Finding pass(String reason, Citation... citations) {
        return new Finding(Outcome.PASS, reason, List.of(citations));
    }

    static Finding fail(String reason, Citation... citations) {
        return new Finding(Outcome.FAIL, reason, List.of(citations));
    }

    static Finding review(String reason, Citation... citations) {
        return new Finding(Outcome.REVIEW, reason, List.of(citations));
    }

    Finding citing(List<? extends Citation> more) {
        return new Finding(outcome, reason, Stream.concat(citations.stream(), more.stream()).distinct().toList());
    }

    /** The worst of several findings decides; the reasons and citations of every finding that gave it are kept. */
    static Finding worst(List<Finding> findings) {
        Outcome worst = findings.stream().map(Finding::outcome).max(SEVERITY).orElseThrow();
        List<Finding> deciding = findings.stream().filter(finding -> finding.outcome() == worst).toList();
        return new Finding(worst,
                String.join("; ", deciding.stream().map(Finding::reason).distinct().toList()),
                deciding.stream().flatMap(finding -> finding.citations().stream()).distinct().toList());
    }

    /** The worst outcome decides, and every finding stays: a Noted clause is shown beside a Blocking one (ADR 0003). */
    static Finding all(List<Finding> findings) {
        Outcome worst = findings.stream().map(Finding::outcome).max(SEVERITY).orElseThrow();
        return new Finding(worst,
                String.join("; ", findings.stream().map(Finding::reason).distinct().toList()),
                findings.stream().flatMap(finding -> finding.citations().stream()).distinct().toList());
    }

    /** The best of several ways to meet a rule: one Pass is enough, otherwise one Review. */
    static Finding best(List<Finding> ways) {
        if (ways.isEmpty()) {
            return review("the rule names no investor type");
        }
        Outcome best = ways.stream().map(Finding::outcome).min(SEVERITY).orElseThrow();
        return ways.stream().filter(way -> way.outcome() == best).findFirst().orElseThrow();
    }
}
