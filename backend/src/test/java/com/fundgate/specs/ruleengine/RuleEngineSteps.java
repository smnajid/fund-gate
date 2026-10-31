package com.fundgate.specs.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.citation.Citation;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.Verdict;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.Rule.Revision;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.ruleengine.UnmodelledRestrictionKind.UnmodelledRestriction;
import com.fundgate.shareclasses.Isin;
import com.fundgate.specs.AnsweringSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

/** The Rule Cards a scenario starts from, and the Verdict and rule outcomes of the Eligibility Check it ran. */
public class RuleEngineSteps {

    /** The demo classes with a Validated Rule Card (demo-dataset.md); BNP-AAC has only a Draft. */
    private static final List<String> SAMPLE_CLASSES =
            List.of("GCE-BP", "GCE-BI", "SIF-W", "BNP-A", "BNP-I", "SL-ICAP", "SL-AMCAP", "SL-IA1");

    /** An Unmodelled Restriction as the specification names it, by its class and the words its Citation quotes. */
    private record Clause(String shareClass, String quoted) {
    }

    private static final Map<String, Clause> CLAUSES = Map.of(
            "reject or close", new Clause("GCE-BP", "Reject or cancel any application"),
            "at AIFM discretion", new Clause("SIF-W", "at the discretion of the AIFM"));

    /** The answer key's column names. */
    private static final Map<String, RuleKind> COLUMNS = Map.of(
            "investor category", RuleKind.INVESTOR_CATEGORY,
            "US Person exclusion", RuleKind.US_PERSON_EXCLUSION,
            "minimum", RuleKind.MINIMUM_INITIAL_SUBSCRIPTION,
            "mandate-only class", RuleKind.MANDATE_ONLY_CLASS,
            "KID for retail", RuleKind.KID_REQUIRED_FOR_RETAIL,
            "Unmodelled", RuleKind.UNMODELLED_RESTRICTION,
            "Target Market", RuleKind.TARGET_MARKET,
            "Fund Registration", RuleKind.FUND_REGISTRATION);

    @Autowired
    private AnsweringSteps answering;

    @Autowired
    private EditableRuleCards ruleCards;

    @Autowired
    private ListedEditions fundDocuments;

    @Before
    public void startFromTheFixtures() {
        ruleCards.reset();
        fundDocuments.reset();
    }

    @Given("the Validated Rule Cards of the sample funds")
    public void theValidatedRuleCardsOfTheSampleFunds() {
        SAMPLE_CLASSES.forEach(code -> assertThat(ruleCards.forShareClass(AnsweringSteps.shareClass(code)))
                .as("the Rule Card of %s", code)
                .hasValueSatisfying(card -> assertThat(card.status()).isEqualTo(RuleCard.Status.VALIDATED)));
    }

    @Given("Nordea's \"reject or close\" clause and the SIF's \"at AIFM discretion\" clause are marked Noted")
    public void theNordeaAndSifClausesAreMarkedNoted() {
        Stream.of("reject or close", "at AIFM discretion").forEach(clause ->
                assertThat(((UnmodelledRestriction) clause(clause).condition()).status())
                        .as("the %s clause", clause)
                        .isEqualTo(UnmodelledRestriction.Status.NOTED));
    }

    @Given("^(?:Nordea's|the SIF's) \"(.+)\" clause is Blocking on ([A-Z]+-[A-Z0-9]+)'s Rule Card$")
    public void theClauseIsBlocking(String clause, String shareClass) {
        RuleCard card = card(shareClass);
        Rule noted = clause(clause);
        Condition blocking = new UnmodelledRestriction(UnmodelledRestriction.Status.BLOCKING, Optional.empty());
        List<Revision> history = new ArrayList<>(noted.history());
        history.add(new Revision(Revision.Action.EDITED, "Rule Card Validator", Instant.parse("2026-11-04T09:00:00Z"),
                blocking));
        Rule edited = new Rule(noted.scope(), noted.shareClass(), blocking, noted.citations(), noted.summary(),
                noted.confidence(), noted.contradiction(), history);
        ruleCards.save(new RuleCard(card.fundId(), card.fundName(), card.shareClasses(), card.version() + 1,
                card.status(), card.rules().stream().map(rule -> rule.equals(noted) ? edited : rule).toList(),
                card.sources(), card.drafting(), card.validation()));
    }

    @Given("^([A-Z]+-[A-Z0-9]+) has only a Draft Rule Card$")
    public void hasOnlyADraftRuleCard(String shareClass) {
        assertThat(ruleCards.forShareClass(AnsweringSteps.shareClass(shareClass)))
                .as("the Rule Card of %s in the demo dataset", shareClass)
                .hasValueSatisfying(card -> assertThat(card.status()).isEqualTo(RuleCard.Status.DRAFT));
    }

    @Given("^the fund master data lists a newer prospectus edition than the one ([A-Z]+-[A-Z0-9]+)'s Rule Card was drafted from$")
    public void theFundMasterDataListsANewerProspectusEdition(String shareClass) {
        RuleCard card = card(shareClass);
        FundDocument prospectus = prospectus(AnsweringSteps.shareClass(shareClass), card);
        String drafted = card.sources().stream()
                .filter(source -> source.documentId().equals(prospectus.documentId()))
                .flatMap(source -> Stream.of(source.fmdEditionDate(), source.pdfEditionDate()).flatMap(Optional::stream))
                .findFirst()
                .orElseThrow();
        fundDocuments.list(prospectus.documentId(), (Integer.parseInt(drafted.substring(0, 4)) + 1) + "-01");
    }

    @Then("^the Verdict is (Eligible|Not Eligible|Needs Review)$")
    public void theVerdictIs(String verdict) {
        assertThat(onlyCheck().result().verdict()).isEqualTo(verdict(verdict));
    }

    @Then("^the (.+) rule gives (Pass|Fail|Review|Warning)$")
    public void theRuleGives(String rule, String outcome) {
        assertThat(outcomeOf(ruleKind(rule)))
                .as("the outcome of the %s rule", rule)
                .isEqualTo(Optional.of(RuleOutcome.Outcome.valueOf(outcome.toUpperCase(Locale.ROOT))));
    }

    @Then("the Justification says {string}")
    public void theJustificationSays(String notice) {
        assertThat(onlyCheck().result().justification().notices()).contains(notice);
    }

    @Then("the Justification warns {string}")
    public void theJustificationWarns(String warning) {
        assertThat(onlyCheck().result().justification().notices()).anyMatch(notice -> notice.startsWith(warning));
    }

    @Then("^the Justification shows (?:Nordea's|the SIF's) \"(.+)\" clause as Noted, with its Citation$")
    public void theJustificationShowsTheClauseAsNoted(String clause) {
        RuleOutcome unmodelled = onlyOutcome(RuleKind.UNMODELLED_RESTRICTION);
        assertThat(unmodelled.outcome()).isEqualTo(RuleOutcome.Outcome.PASS);
        assertThat(unmodelled.reason()).contains("Noted");
        assertThat(unmodelled.citations())
                .anyMatch(citation -> citation instanceof Citation.Document document
                        && document.quote().contains(CLAUSES.get(clause).quoted()));
    }

    @Then("both checks give the same Verdict and the same Justification")
    public void bothChecksGiveTheSameVerdictAndJustification() {
        assertThat(answering.previous().checks()).hasSize(1);
        assertThat(onlyCheck().result()).isEqualTo(answering.previous().checks().getFirst().result());
    }

    @Then("every rule outcome cites a passage of the Fund Documentation or a field of the Client Profile")
    public void everyRuleOutcomeCites() {
        assertThat(onlyCheck().result().justification().outcomes())
                .isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.citations())
                        .as("the citations of the %s rule", outcome.rule())
                        .anyMatch(citation -> citation instanceof Citation.Document
                                || citation instanceof Citation.ClientProfileField));
    }

    @Then("the investor category rule cites the prospectus page it relies on")
    public void theInvestorCategoryRuleCitesTheProspectusPage() {
        Answer.Check check = onlyCheck();
        FundDocument prospectus = prospectus(check.request().shareClass(),
                ruleCards.forShareClass(check.request().shareClass()).orElseThrow());
        assertThat(onlyOutcome(RuleKind.INVESTOR_CATEGORY).citations())
                .anyMatch(citation -> citation instanceof Citation.Document document
                        && document.document().equals(prospectus.documentId())
                        && document.page() > 0
                        && document.quoteFoundOnPage().equals(Optional.of(true)));
    }

    @Then("the rule outcomes are:")
    public void theRuleOutcomesAre(DataTable table) {
        Map<String, String> expected = new LinkedHashMap<>(table.asMap());
        Map<String, String> actual = new LinkedHashMap<>();
        expected.keySet().forEach(column -> actual.put(column, outcomeOf(Optional.ofNullable(COLUMNS.get(column))
                        .orElseThrow(() -> new IllegalArgumentException("No rule column named " + column)))
                .map(outcome -> outcome.name().charAt(0) + outcome.name().substring(1).toLowerCase(Locale.ROOT))
                .orElse("—")));
        // "Warning (no source)" names why the Target Market only warns; the outcome is the Warning
        expected.replaceAll((column, outcome) -> outcome.replaceFirst(" \\(.*\\)$", ""));
        assertThat(actual).isEqualTo(expected);
    }

    private Optional<RuleOutcome.Outcome> outcomeOf(RuleKind kind) {
        List<RuleOutcome> outcomes = onlyCheck().result().justification().outcomes().stream()
                .filter(outcome -> outcome.rule() == kind)
                .toList();
        assertThat(outcomes).as("the outcomes of the %s rule", kind).hasSizeLessThanOrEqualTo(1);
        return outcomes.stream().findFirst().map(RuleOutcome::outcome);
    }

    private RuleOutcome onlyOutcome(RuleKind kind) {
        return onlyCheck().result().justification().outcomes().stream()
                .filter(outcome -> outcome.rule() == kind)
                .reduce((first, second) -> {
                    throw new AssertionError("Two outcomes of the " + kind + " rule");
                })
                .orElseThrow(() -> new AssertionError("No outcome of the " + kind + " rule"));
    }

    private Answer.Check onlyCheck() {
        assertThat(answering.answered().checks()).as("the Eligibility Checks run").hasSize(1);
        return answering.answered().checks().getFirst();
    }

    private RuleCard card(String shareClass) {
        return ruleCards.forShareClass(AnsweringSteps.shareClass(shareClass)).orElseThrow();
    }

    /** The class's Unmodelled Restriction whose Citation quotes the clause. */
    private Rule clause(String clause) {
        Clause named = CLAUSES.get(clause);
        Isin isin = AnsweringSteps.shareClass(named.shareClass());
        return card(named.shareClass()).rules().stream()
                .filter(rule -> rule.condition() instanceof UnmodelledRestriction)
                .filter(rule -> rule.shareClass().isEmpty() || rule.shareClass().equals(Optional.of(isin)))
                .filter(rule -> rule.citations().stream().anyMatch(citation -> citation.quote().contains(named.quoted())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + clause + " clause on the card of " + named.shareClass()));
    }

    /** The prospectus the card was drafted from, as the fund master data types it. */
    private FundDocument prospectus(Isin shareClass, RuleCard card) {
        return fundDocuments.documents(shareClass).stream()
                .filter(document -> document.type().equals(Optional.of(FundDocument.Type.PROSPECTUS)))
                .filter(document -> card.sources().stream()
                        .anyMatch(source -> source.documentId().equals(document.documentId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No prospectus among the sources of the card of " + shareClass));
    }

    /** "Not Eligible" as NOT_ELIGIBLE. */
    public static Verdict verdict(String verdict) {
        return Verdict.valueOf(verdict.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /**
     * The rule as the specification names it, e.g. "mandate-only class" or "Unmodelled Restrictions". An unknown name
     * is a wording the glue does not know, so it errors rather than failing as "not built yet".
     */
    public static RuleKind ruleKind(String rule) {
        String name = rule.toUpperCase(Locale.ROOT).replaceAll("[ -]", "_");
        return Arrays.stream(RuleKind.values())
                .filter(candidate -> name.equals(candidate.name()) || name.equals(candidate.name() + "S"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No rule kind named " + rule));
    }
}
