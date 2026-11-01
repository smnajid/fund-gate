package com.fundgate.validation;

import com.fundgate.citation.Citation;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.shareclasses.Isin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the validation screen shows of one card version (screens.md): each rule with its status and the signals the
 * code checks, and the contradictions to settle. The model's self-rated confidence stays on the card, off the screen
 * (hypotheses.md, Validation confidence, #34).
 */
public record CardReview(
        RuleCard card,
        List<RuleReview> rules,
        List<Contradiction> contradictions,
        boolean everyQuoteFoundOnPage,
        boolean readyToValidate,
        /** This version is the one Eligibility Checks read for its classes. */
        boolean inForce) {

    public CardReview {
        rules = List.copyOf(rules);
        contradictions = List.copyOf(contradictions);
    }

    public enum Status { OPEN, ACCEPTED, CORRECTED, REJECTED, FOLLOWED, ADDED_BY_VALIDATOR }

    /** One rule, by its place on the card; kind is its name in a card's JSON, e.g. investor_category. */
    public record RuleReview(int index, String kind, Rule rule, Status status, Signals signals) {
    }

    /** Checked by code, never rated by the model. */
    public record Signals(boolean quoteFoundOnPage, boolean noSupportingQuote, boolean unsettledContradiction) {
    }

    /** Two or more documents' readings of one kind of rule for one scope; settled once one is followed. */
    public record Contradiction(String kind, Optional<Isin> shareClass, List<Integer> rules, boolean settled) {

        public Contradiction {
            rules = List.copyOf(rules);
        }
    }

    /** {@code kinds} names each kind's condition, as the reader of the cards registers them (RuleCatalog). */
    public static CardReview of(RuleCard card, Map<String, Class<? extends Condition>> kinds, boolean inForce) {
        List<RuleReview> rules = new ArrayList<>();
        Map<List<Object>, List<Integer>> contradicted = new LinkedHashMap<>();
        for (int index = 0; index < card.rules().size(); index++) {
            Rule rule = card.rules().get(index);
            String kind = kindOf(rule.condition(), kinds);
            rules.add(new RuleReview(index, kind, rule, status(rule), new Signals(
                    quotesFound(rule),
                    rule.citations().isEmpty(),
                    !RuleCardValidation.settled(rule))));
            if (rule.contradiction()) {
                contradicted.computeIfAbsent(List.of(kind, rule.scope(), rule.shareClass()), key -> new ArrayList<>())
                        .add(index);
            }
        }
        List<Contradiction> contradictions = contradicted.entrySet().stream()
                .map(group -> new Contradiction((String) group.getKey().get(0),
                        card.rules().get(group.getValue().getFirst()).shareClass(), group.getValue(),
                        group.getValue().stream().allMatch(index -> RuleCardValidation.settled(card.rules().get(index)))))
                .toList();
        boolean everyQuote = card.rules().stream().allMatch(CardReview::quotesFound);
        return new CardReview(card, rules, contradictions, everyQuote, RuleCardValidation.readyToValidate(card),
                inForce);
    }

    private static boolean quotesFound(Rule rule) {
        return !rule.citations().isEmpty() && rule.citations().stream()
                .map(Citation.Document::quoteFoundOnPage)
                .allMatch(found -> found.equals(Optional.of(true)));
    }

    private static Status status(Rule rule) {
        if (rule.history().isEmpty()) {
            return Status.OPEN;
        }
        return switch (rule.history().getLast().action()) {
            case DRAFTED -> Status.OPEN;
            case ACCEPTED -> Status.ACCEPTED;
            case EDITED -> Status.CORRECTED;
            case REJECTED -> Status.REJECTED;
            case CONTRADICTION_RESOLVED -> Status.FOLLOWED;
            case ADDED_BY_VALIDATOR -> Status.ADDED_BY_VALIDATOR;
        };
    }

    private static String kindOf(Condition condition, Map<String, Class<? extends Condition>> kinds) {
        return kinds.entrySet().stream()
                .filter(kind -> kind.getValue().isInstance(condition))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No kind for " + condition.getClass().getName()));
    }
}
