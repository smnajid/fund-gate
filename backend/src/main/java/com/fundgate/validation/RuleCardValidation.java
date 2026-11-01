package com.fundgate.validation;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.Rule.Revision;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.shareclasses.Isin;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a Rule Card Validator does to a Draft card (ADR 0001, ADR 0003): accepts, corrects or rejects each rule,
 * settles each contradiction by following one document, and validates the card once every rule is reviewed. Each
 * step is appended to the rule's history with who and when; marking an Unmodelled Restriction Noted is a correction.
 * The steps run one at a time, so a step read from the card is never lost to one saved meanwhile.
 */
public class RuleCardValidation {

    private final RuleCardVersions versions;
    private final Map<String, Class<? extends Condition>> kinds;
    private final Clock clock;

    /** {@code kinds} names each kind's condition, as the reader of the cards registers them (RuleCatalog). */
    public RuleCardValidation(RuleCardVersions versions, Map<String, Class<? extends Condition>> kinds, Clock clock) {
        this.versions = versions;
        this.kinds = Map.copyOf(kinds);
        this.clock = clock;
    }

    /** The latest version of each fund, as the validator picks one. */
    public List<CardReview> cards() {
        return versions.latest().stream().map(this::review).toList();
    }

    /** Every version of the fund the class belongs to, oldest first. */
    public List<CardReview> versions(Isin shareClass) {
        return versions.versionsOf(shareClass).stream().map(this::review).toList();
    }

    public CardReview review(Isin shareClass, int version) {
        return review(versions.version(shareClass, version).orElseThrow(() -> new IllegalArgumentException(
                "No version " + version + " of the Rule Card of " + shareClass.value())));
    }

    private CardReview review(RuleCard card) {
        boolean inForce = versions.forShareClass(card.shareClasses().getFirst())
                .map(decides -> decides.version() == card.version()).orElse(false);
        return CardReview.of(card, kinds, inForce);
    }

    public synchronized RuleCard accept(Isin shareClass, int version, int rule, String validator) {
        return revise(shareClass, version, rule, validator, Revision.Action.ACCEPTED, Optional.empty());
    }

    /** The condition the validator read on the cited page instead of the drafted one. */
    public synchronized RuleCard correct(Isin shareClass, int version, int rule, Condition corrected, String validator) {
        return revise(shareClass, version, rule, validator, Revision.Action.EDITED, Optional.of(corrected));
    }

    /** A rule the documents do not support: it stays in the history and never decides. */
    public synchronized RuleCard reject(Isin shareClass, int version, int rule, String validator) {
        return revise(shareClass, version, rule, validator, Revision.Action.REJECTED, Optional.empty());
    }

    /**
     * Settles a contradiction: this rule's document governs, and the rules of the same kind and scope that
     * contradict it are rejected.
     */
    public synchronized RuleCard follow(Isin shareClass, int version, int rule, String validator) {
        RuleCard card = draft(shareClass, version);
        Rule followed = ruleAt(card, rule);
        if (!followed.contradiction()) {
            throw new IllegalArgumentException("Rule " + rule + " contradicts no other document");
        }
        List<Rule> rules = new ArrayList<>(card.rules());
        for (int index = 0; index < rules.size(); index++) {
            Rule other = rules.get(index);
            if (index == rule) {
                rules.set(index, revised(other, Revision.Action.CONTRADICTION_RESOLVED, validator, Optional.empty()));
            } else if (rivals(followed, other)) {
                rules.set(index, revised(other, Revision.Action.REJECTED, validator, Optional.empty()));
            }
        }
        return save(card, rules);
    }

    /** Records who validated this version and when; from then on it decides Verdicts for its classes. */
    public synchronized RuleCard validate(Isin shareClass, int version, String validator) {
        named(validator);
        RuleCard card = draft(shareClass, version);
        if (!readyToValidate(card)) {
            throw new IllegalStateException("Every rule must be reviewed and every contradiction settled first, "
                    + "and at least one rule left to decide");
        }
        RuleCard validated = new RuleCard(card.fundId(), card.fundName(), card.shareClasses(), card.version(),
                RuleCard.Status.VALIDATED, card.rules(), card.sources(), card.drafting(),
                Optional.of(new RuleCard.Validation(validator.strip(), clock.instant())));
        versions.replaceDraft(validated);
        return validated;
    }

    /**
     * A Draft whose every rule is reviewed and whose every contradiction is settled. A card left with no rule that
     * decides is never ready: validated, it would let Fund Registration alone give Eligible.
     */
    public static boolean readyToValidate(RuleCard card) {
        return card.status() == RuleCard.Status.DRAFT
                && card.rules().stream().anyMatch(rule -> !rejected(rule))
                && card.rules().stream().allMatch(rule -> rule.reviewed() && settled(rule));
    }

    /** A contradicted rule is settled once followed, or rejected in favour of another document. */
    public static boolean settled(Rule rule) {
        return !rule.contradiction() || rule.contradictionResolved() || rejected(rule);
    }

    public static boolean rejected(Rule rule) {
        return !rule.history().isEmpty() && rule.history().getLast().action() == Revision.Action.REJECTED;
    }

    private RuleCard revise(Isin shareClass, int version, int rule, String validator, Revision.Action action,
            Optional<Condition> corrected) {
        RuleCard card = draft(shareClass, version);
        List<Rule> rules = new ArrayList<>(card.rules());
        rules.set(rule, revised(ruleAt(card, rule), action, validator, corrected));
        return save(card, rules);
    }

    private Rule revised(Rule rule, Revision.Action action, String validator, Optional<Condition> corrected) {
        named(validator);
        Condition condition = corrected.orElse(rule.condition());
        List<Revision> history = new ArrayList<>(rule.history());
        history.add(new Revision(action, validator.strip(), clock.instant(), condition));
        return new Rule(rule.scope(), rule.shareClass(), condition, rule.citations(), rule.summary(),
                rule.confidence(), rule.contradiction(), history);
    }

    private static void named(String validator) {
        if (validator == null || validator.isBlank()) {
            throw new IllegalArgumentException("Every review step names its validator");
        }
    }

    private RuleCard save(RuleCard card, List<Rule> rules) {
        RuleCard reviewed = new RuleCard(card.fundId(), card.fundName(), card.shareClasses(), card.version(),
                card.status(), rules, card.sources(), card.drafting(), card.validation());
        versions.replaceDraft(reviewed);
        return reviewed;
    }

    private RuleCard draft(Isin shareClass, int version) {
        RuleCard card = versions.version(shareClass, version).orElseThrow(() -> new IllegalArgumentException(
                "No version " + version + " of the Rule Card of " + shareClass.value()));
        if (card.status() != RuleCard.Status.DRAFT) {
            throw new IllegalStateException("Version " + version + " is Validated: draft a new version instead");
        }
        return card;
    }

    private static Rule ruleAt(RuleCard card, int rule) {
        if (rule < 0 || rule >= card.rules().size()) {
            throw new IllegalArgumentException("No rule " + rule + " on version " + card.version());
        }
        return card.rules().get(rule);
    }

    /** Same kind, same scope, both flagged: two documents' readings of one rule. */
    private static boolean rivals(Rule followed, Rule other) {
        return other != followed && other.contradiction()
                && other.condition().getClass().equals(followed.condition().getClass())
                && other.scope() == followed.scope()
                && Objects.equals(other.shareClass(), followed.shareClass());
    }
}
