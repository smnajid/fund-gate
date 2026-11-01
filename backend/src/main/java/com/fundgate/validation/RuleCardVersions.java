package com.fundgate.validation;

import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.shareclasses.Isin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every version of every Rule Card, Draft or Validated: the cards the Rule Engine reads and the Rule Card Validator
 * works on. A Validated version is never replaced, only superseded by a newer one; a Draft is replaced while it is
 * reviewed, and once validated. Two versions belong to the same fund when they cover a Share Class in common.
 * Held in memory: a restart starts again from the card files.
 */
public class RuleCardVersions implements RuleCards {

    private final List<RuleCard> versions = new ArrayList<>();

    /** The cards as their files number them, e.g. the Validated fixtures. */
    public RuleCardVersions(List<RuleCard> cards) {
        for (RuleCard card : cards) {
            // Two files of one version: which one decides, or is validated, would be a guess
            if (versions.stream().anyMatch(other -> sameFund(card, other) && other.version() == card.version())) {
                throw new IllegalArgumentException("Two Rule Cards of " + card.fundName() + " are version "
                        + card.version());
            }
            versions.add(card);
        }
    }

    /** The latest Validated version decides while a newer one is still a Draft (Stale Rule Card, GLOSSARY.md). */
    @Override
    public synchronized Optional<RuleCard> forShareClass(Isin shareClass) {
        List<RuleCard> covering = versionsOf(shareClass);
        return covering.stream()
                .filter(card -> card.status() == RuleCard.Status.VALIDATED)
                .reduce((older, newer) -> newer)
                .or(() -> covering.isEmpty() ? Optional.empty() : Optional.of(covering.getLast()));
    }

    /** Every version of the fund the class belongs to, oldest first. */
    public synchronized List<RuleCard> versionsOf(Isin shareClass) {
        return versions.stream()
                .filter(card -> card.shareClasses().contains(shareClass))
                .sorted(Comparator.comparingInt(RuleCard::version))
                .toList();
    }

    public synchronized Optional<RuleCard> version(Isin shareClass, int version) {
        return versionsOf(shareClass).stream().filter(card -> card.version() == version).findFirst();
    }

    /** The latest version of each fund, for the validator to pick one. */
    public synchronized List<RuleCard> latest() {
        List<RuleCard> latest = new ArrayList<>();
        for (RuleCard card : versions) {
            if (latest.stream().noneMatch(other -> sameFund(card, other))) {
                latest.add(versionsOf(card.shareClasses().getFirst()).getLast());
            }
        }
        return Collections.unmodifiableList(latest);
    }

    /**
     * A Draft the pipeline wrote, numbered after the fund's latest version: the pipeline numbers every draft 1, as
     * it does not know the versions already validated.
     */
    public synchronized RuleCard addDraft(RuleCard draft) {
        if (draft.status() != RuleCard.Status.DRAFT) {
            throw new IllegalArgumentException("Only a Draft card is added; a card is Validated by its validator");
        }
        int next = versions.stream().filter(card -> sameFund(card, draft)).mapToInt(RuleCard::version).max()
                .orElse(0) + 1;
        RuleCard numbered = new RuleCard(draft.fundId(), draft.fundName(), draft.shareClasses(), next,
                draft.status(), draft.rules(), draft.sources(), draft.drafting(), draft.validation());
        versions.add(numbered);
        return numbered;
    }

    /** The same version of a Draft, reviewed further or validated. A Validated version is never replaced. */
    synchronized void replaceDraft(RuleCard reviewed) {
        RuleCard current = version(reviewed.shareClasses().getFirst(), reviewed.version())
                .orElseThrow(() -> new IllegalArgumentException("No version " + reviewed.version() + " of "
                        + reviewed.fundName()));
        if (current.status() != RuleCard.Status.DRAFT) {
            throw new IllegalStateException("Version " + current.version() + " of " + current.fundName()
                    + " is Validated: it is superseded by a new version, never edited");
        }
        versions.set(versions.indexOf(current), reviewed);
    }

    private static boolean sameFund(RuleCard card, RuleCard other) {
        return card.shareClasses().stream().anyMatch(other.shareClasses()::contains);
    }
}
