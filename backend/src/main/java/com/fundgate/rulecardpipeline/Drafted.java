package com.fundgate.rulecardpipeline;

import com.fundgate.citation.Citation;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import java.util.List;
import java.util.Optional;

/** What one run of the pipeline gives for one fund: a Draft Rule Card, or the reason it drafted none. */
public sealed interface Drafted {

    /** Always a Draft: only a Rule Card Validator makes a card Validated (ADR 0001). */
    record Draft(RuleCard card) implements Drafted {

        /** Quotes not found on the page they cite. They stay on the card, flagged, for the validator to see. */
        public List<Citation.Document> quotesNotFound() {
            return card.rules().stream()
                    .flatMap(rule -> rule.citations().stream())
                    .filter(citation -> !citation.quoteFoundOnPage().equals(Optional.of(true)))
                    .toList();
        }

        /** Rules the model gave no quote for: no Citation backs them. */
        public List<Rule> rulesWithoutQuote() {
            return card.rules().stream().filter(rule -> rule.citations().isEmpty()).toList();
        }
    }

    /** No card this run. The reason names the document or the step, never a prompt. */
    record Refused(String reason) implements Drafted {
    }
}
