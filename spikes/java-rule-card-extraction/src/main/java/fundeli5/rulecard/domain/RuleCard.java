package fundeli5.rulecard.domain;

import java.util.List;
import java.util.Map;

/**
 * The structured set of Hard Restrictions and Target Market criteria for the Share Classes of one Fund,
 * each rule carrying its Citations. An extracted card is always a Draft: only a Rule Card Validator
 * makes it Validated (ADR 0001). Selling restrictions found in the documents are kept beside the card as
 * proposed Fund Registration entries, which are not part of the Rule Card.
 */
public record RuleCard(String fund, Map<String, String> shareClasses, Status status, List<Rule> rules,
        List<Rule> fundRegistration) {

    public enum Status {
        DRAFT, VALIDATED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public static RuleCard draft(String fund, Map<String, String> shareClasses, List<Rule> rules) {
        return new RuleCard(fund, Map.copyOf(shareClasses), Status.DRAFT,
                rules.stream().filter(r -> r.kind() != RuleKind.FUND_REGISTRATION).toList(),
                rules.stream().filter(r -> r.kind() == RuleKind.FUND_REGISTRATION).toList());
    }

    public long verifiedQuotes() {
        return citations().filter(c -> Boolean.TRUE.equals(c.verified())).count();
    }

    public long quotes() {
        return citations().count();
    }

    private java.util.stream.Stream<Citation> citations() {
        return java.util.stream.Stream.concat(rules.stream(), fundRegistration.stream()).flatMap(r -> r.citations().stream());
    }
}
