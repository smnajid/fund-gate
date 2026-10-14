package fundeli5.rulecard.application;

import java.util.List;

import fundeli5.rulecard.application.port.out.RuleCardDrafter.Usage;
import fundeli5.rulecard.domain.RuleCard;

/** One extraction: what was sent, what it cost, and the Draft Rule Card it produced. */
public record ExtractionRun(String label, String drafter, List<Integer> selectedPages, List<Usage> usage,
                            double totalSeconds, double totalUsd, RuleCard card) {
}
