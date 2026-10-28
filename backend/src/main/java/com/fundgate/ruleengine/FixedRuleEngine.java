package com.fundgate.ruleengine;

import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.RuleOutcome.Outcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.Verdict;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.fxrates.FxRates;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.shareclasses.Isin;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides each check from the class's Validated Rule Card with fixed rules, no LLM (ADR 0001, ADR 0002): one outcome
 * per rule kind, each cited. Any Fail gives Not Eligible; otherwise any Review gives Needs Review; otherwise Eligible.
 */
public class FixedRuleEngine implements RuleEngine {

    /** Switzerland and Luxembourg, by the Legal Entity holding the portfolio (issue #6). */
    private static final Set<String> COVERED_LEGAL_ENTITIES = Set.of("LOC", "LUX");

    private final RuleCards ruleCards;
    private final FundDocuments fundDocuments;
    private final HardRestrictions hardRestrictions;
    private final FundRegistrationRule fundRegistration;

    public FixedRuleEngine(RuleCards ruleCards, FundDocuments fundDocuments, FundRegistrations fundRegistrations,
            FxRates fxRates) {
        Money money = new Money(fxRates);
        InvestorTypes investorTypes = new InvestorTypes(money);
        this.ruleCards = ruleCards;
        this.fundDocuments = fundDocuments;
        this.hardRestrictions = new HardRestrictions(investorTypes, money);
        this.fundRegistration = new FundRegistrationRule(fundRegistrations, investorTypes);
    }

    @Override
    public EligibilityResult check(SubscriptionRequest request, ClientProfile profile) {
        if (!COVERED_LEGAL_ENTITIES.contains(profile.legalEntity())) {
            return needsReview("regime not covered");
        }
        Optional<RuleCard> card = ruleCards.forShareClass(request.shareClass());
        if (card.isEmpty()) {
            return needsReview("Fund not covered");
        }
        if (card.get().status() != RuleCard.Status.VALIDATED || card.get().validation().isEmpty()
                || !card.get().rules().stream().allMatch(Rule::reviewed)) {
            return needsReview("Rule Card not validated");
        }
        Facts facts = new Facts(request, profile);
        Map<RuleKind, List<Finding>> findings = new EnumMap<>(RuleKind.class);
        for (Rule rule : rulesFor(card.get(), request.shareClass())) {
            hardRestrictions.evaluate(rule.condition(), facts)
                    .map(finding -> contradicted(rule, finding).citing(rule.citations()))
                    .ifPresent(finding -> findings.computeIfAbsent(rule.condition().kind(), kind -> new ArrayList<>())
                            .add(finding));
        }
        findings.put(RuleKind.FUND_REGISTRATION, List.of(fundRegistration.evaluate(facts)));

        List<RuleOutcome> outcomes = findings.entrySet().stream()
                .map(kind -> outcome(kind.getKey(), kind.getKey() == RuleKind.UNMODELLED_RESTRICTION
                        ? Finding.all(kind.getValue())
                        : Finding.worst(kind.getValue())))
                .toList();
        return new EligibilityResult(verdict(outcomes),
                new Justification(outcomes, staleWarning(card.get(), request.shareClass()).stream().toList()));
    }

    /**
     * A Stale Rule Card still decides, with a warning: FMD lists an edition of a type of document (the prospectus,
     * a KID) newer than the edition of that type the card was drafted from. Editions are dates or YYYY-MM:
     * 2026-06-29 is the 2026-06 edition.
     */
    private Optional<String> staleWarning(RuleCard card, Isin shareClass) {
        List<FundDocument> listed = fundDocuments.documents(shareClass).stream()
                .filter(document -> document.isins().contains(shareClass))
                .toList();
        Map<DocumentId, FundDocument.Type> types = listed.stream()
                .filter(document -> document.type().isPresent())
                .collect(Collectors.toMap(FundDocument::documentId, document -> document.type().get(), (a, b) -> a));
        Map<FundDocument.Type, String> drafted = new EnumMap<>(FundDocument.Type.class);
        for (RuleCard.Source source : card.sources()) {
            FundDocument.Type type = types.get(source.documentId());
            Stream.of(source.fmdEditionDate(), source.pdfEditionDate()).flatMap(Optional::stream).findFirst()
                    .filter(edition -> type != null)
                    .ifPresent(edition -> drafted.merge(type, edition, FixedRuleEngine::latest));
        }
        return listed.stream()
                .filter(document -> document.type().isPresent() && document.fmdEditionDate().isPresent())
                .filter(document -> drafted.containsKey(document.type().get()))
                .filter(document -> newer(document.fmdEditionDate().get(), drafted.get(document.type().get())))
                .findFirst()
                .map(document -> "Stale Rule Card: the fund master data lists the " + document.fmdEditionDate().get()
                        + " edition of the " + Facts.lower(document.type().get()) + ", the card was drafted from the "
                        + drafted.get(document.type().get()) + " edition");
    }

    private static String latest(String one, String other) {
        return newer(one, other) ? one : other;
    }

    /** Fund-level rules apply to every class; a class-level rule overrides the fund-level rules of its kind. */
    private static List<Rule> rulesFor(RuleCard card, Isin shareClass) {
        List<Rule> kept = card.rules().stream().filter(rule -> !rejected(rule)).toList();
        List<Rule> classRules = kept.stream()
                .filter(rule -> rule.shareClass().equals(Optional.of(shareClass)))
                .toList();
        Set<RuleKind> overridden = classRules.stream()
                .map(rule -> rule.condition().kind())
                .filter(kind -> kind != RuleKind.UNMODELLED_RESTRICTION)
                .collect(Collectors.toSet());
        List<Rule> rules = new ArrayList<>(kept.stream()
                .filter(rule -> rule.shareClass().isEmpty() && !overridden.contains(rule.condition().kind()))
                .toList());
        rules.addAll(classRules);
        return rules;
    }

    /** A rule the validator rejected stays in the card's history but never decides. */
    private static boolean rejected(Rule rule) {
        return !rule.history().isEmpty()
                && rule.history().getLast().action() == Rule.Revision.Action.REJECTED;
    }

    private static boolean newer(String edition, String than) {
        int precision = Math.min(edition.length(), than.length());
        return edition.substring(0, precision).compareTo(than.substring(0, precision)) > 0;
    }

    /** Two documents disagreeing on a rule give Review until a validator resolves it. */
    private static Finding contradicted(Rule rule, Finding finding) {
        if (rule.contradiction() && !rule.contradictionResolved()) {
            return Finding.review("the Fund Documentation contradicts itself on this rule; " + finding.reason())
                    .citing(finding.citations());
        }
        return finding;
    }

    private static RuleOutcome outcome(RuleKind kind, Finding finding) {
        return new RuleOutcome(kind, finding.outcome(), finding.reason(), finding.citations());
    }

    private static Verdict verdict(List<RuleOutcome> outcomes) {
        if (outcomes.stream().anyMatch(outcome -> outcome.outcome() == Outcome.FAIL)) {
            return Verdict.NOT_ELIGIBLE;
        }
        if (outcomes.stream().anyMatch(outcome -> outcome.outcome() == Outcome.REVIEW)) {
            return Verdict.NEEDS_REVIEW;
        }
        return Verdict.ELIGIBLE;
    }

    private static EligibilityResult needsReview(String notice) {
        return new EligibilityResult(Verdict.NEEDS_REVIEW, new Justification(List.of(), List.of(notice)));
    }
}
