package fundgate.rulecard.application;

import java.util.List;

import fundgate.rulecard.application.port.out.RuleCardDrafter.Usage;
import fundgate.rulecard.domain.RuleCard;

/** One extraction: what was sent, what it cost, and the Draft Rule Card it produced. */
public record ExtractionRun(String label, String drafter, List<Integer> selectedPages, List<Usage> usage,
                            double totalSeconds, double totalUsd, RuleCard card) {
}
