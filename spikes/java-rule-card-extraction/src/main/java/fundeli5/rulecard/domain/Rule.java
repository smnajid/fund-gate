package fundeli5.rulecard.domain;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One rule of a Rule Card for one Share Class ({@code "*"} = every class of the fund).
 * Only the parameters relevant to the kind are set; the others are null.
 */
public record Rule(
        String shareClass,
        Scope scope,
        RuleKind kind,
        List<String> allowedCategories,
        String usPersonDefinition,
        Boolean excludesUsCitizens,
        Double amount,
        String currency,
        Boolean mandateOnly,
        Boolean kidAvailable,
        String dimension,
        String value,
        boolean contradiction,
        double confidence,
        String summary,
        List<Citation> citations) {

    public enum Scope {
        FUND, SHARE_CLASS;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public Rule withCitations(UnaryOperator<Citation> change) {
        return new Rule(shareClass, scope, kind, allowedCategories, usPersonDefinition, excludesUsCitizens, amount,
                currency, mandateOnly, kidAvailable, dimension, value, contradiction, confidence, summary,
                citations.stream().map(change).toList());
    }
}
