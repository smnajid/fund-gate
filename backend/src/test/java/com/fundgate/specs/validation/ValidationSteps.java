package com.fundgate.specs.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.clientprofiles.ClientProfile;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.fundregistrations.FundRegistrations;
import com.fundgate.fxrates.FxRates;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.Rule.Revision;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.ruleengine.FixedRuleEngine;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.ruleengine.UnmodelledRestrictionKind.UnmodelledRestriction;
import com.fundgate.shareclasses.Isin;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.validation.CardReview;
import com.fundgate.validation.RuleCardFiles;
import com.fundgate.validation.RuleCardValidation;
import com.fundgate.validation.RuleCardVersions;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The Rule Card Validator on the validation screen's module (06-validate-a-rule-card.feature). Each scenario starts
 * from the card files, as the dealer app does, with its own versions, so one scenario's validation never leaks.
 */
public class ValidationSteps {

    private static final String VALIDATOR = "Rule Card Validator";
    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    /** Anna's discretionary portfolio: what a check of GCE-BP or GCE-BI reads (demo-dataset.md). */
    private static final PortfolioReference PORTFOLIO = new PortfolioReference("LOC90000102");

    @Autowired
    private FundDocuments fundDocuments;

    @Autowired
    private FundRegistrations fundRegistrations;

    @Autowired
    private FxRates fxRates;

    @Autowired
    private ClientProfiles clientProfiles;

    @Value("${fundeli5.fixtures.dir}")
    private String fixtures;

    private final RuleCardFiles files = new RuleCardFiles(RuleCatalog.conditionTypes());
    private RuleCardVersions versions;
    private RuleCardValidation validation;
    private RuleCard draft;
    private RuleCard firstVersion;

    @Before
    public void startFromTheCardFiles() {
        versions = new RuleCardVersions(files.read(Path.of(fixtures, "rule-cards")));
        validation = new RuleCardValidation(versions, RuleCatalog.conditionTypes(), Clock.fixed(NOW, ZoneOffset.UTC));
        draft = null;
        firstVersion = null;
    }

    @Given("^the Draft Rule Card of (.+)$")
    public void theDraftRuleCardOf(String fund) {
        draft = versions.addDraft(nordeaDraft(fund));
    }

    @Then("the screen shows a contradiction on the investor category of {word}")
    public void theScreenShowsAContradictionOnTheInvestorCategoryOf(String shareClass) {
        assertThat(review().contradictions())
                .filteredOn(contradiction -> contradiction.kind().equals("investor_category"))
                .filteredOn(contradiction -> contradiction.shareClass()
                        .equals(Optional.of(AnsweringSteps.shareClass(shareClass))))
                .singleElement()
                .satisfies(contradiction -> {
                    assertThat(contradiction.settled()).isFalse();
                    assertThat(documentTypes(contradiction))
                            .containsExactlyInAnyOrder(FundDocument.Type.PROSPECTUS, FundDocument.Type.KID);
                });
    }

    @Then("the card cannot be validated")
    public void theCardCannotBeValidated() {
        assertThat(review().readyToValidate()).isFalse();
        assertThatThrownBy(() -> validation.validate(anyClass(), draft.version(), VALIDATOR))
                .isInstanceOf(IllegalStateException.class);
    }

    @When("the validator chooses to follow the prospectus")
    public void theValidatorChoosesToFollowTheProspectus() {
        CardReview.Contradiction contradiction = review().contradictions().stream()
                .filter(open -> !open.settled())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No contradiction to settle"));
        int prospectus = contradiction.rules().stream()
                .filter(index -> documentTypes(draft.rules().get(index)).contains(FundDocument.Type.PROSPECTUS))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reading of the prospectus in the contradiction"));
        draft = validation.follow(anyClass(), draft.version(), prospectus, VALIDATOR);
    }

    @When("accepts or corrects every other rule")
    public void acceptsOrCorrectsEveryOtherRule() {
        review().rules().stream()
                .filter(rule -> rule.status() == CardReview.Status.OPEN)
                .map(CardReview.RuleReview::index)
                .toList()
                .forEach(index -> draft = validation.accept(anyClass(), draft.version(), index, VALIDATOR));
    }

    @Then("the card can be validated")
    public void theCardCanBeValidated() {
        assertThat(review().readyToValidate()).isTrue();
        RuleCard validated = validation.validate(anyClass(), draft.version(), VALIDATOR);
        assertThat(validated.status()).isEqualTo(RuleCard.Status.VALIDATED);
        assertThat(validated.validation()).contains(new RuleCard.Validation(VALIDATOR, NOW));
    }

    @Given("Validated version {int} of {word}'s Rule Card")
    public void validatedVersionOfTheRuleCard(int version, String shareClass) {
        RuleCard deciding = versions.forShareClass(AnsweringSteps.shareClass(shareClass)).orElseThrow();
        assertThat(deciding.version()).isEqualTo(version);
        assertThat(deciding.status()).isEqualTo(RuleCard.Status.VALIDATED);
        firstVersion = deciding;
    }

    @When("the pipeline drafts version {int} from a new prospectus edition")
    public void thePipelineDraftsVersionFromANewProspectusEdition(int version) {
        // The Draft as the pipeline writes it, numbered 1; the store numbers it after the validated version
        RuleCard drafted = nordeaDraft("Nordea 1 Global Climate and Environment");
        List<RuleCard.Source> sources = drafted.sources().stream()
                .map(source -> source.documentId().equals(prospectusOf(firstVersion))
                        ? new RuleCard.Source(source.documentId(), source.contentSha256(), source.fmdEditionDate(),
                                Optional.of("2026-11"), source.confidentiality())
                        : source)
                .toList();
        draft = versions.addDraft(new RuleCard(drafted.fundId(), drafted.fundName(), drafted.shareClasses(),
                drafted.version(), drafted.status(), drafted.rules(), sources, drafted.drafting(),
                drafted.validation()));
        assertThat(draft.version()).isEqualTo(version);
    }

    @Then("version {int} still decides Verdicts")
    public void versionStillDecidesVerdicts(int version) {
        assertDecides(version);
    }

    @Then("version {int} decides Verdicts only once validated")
    public void versionDecidesVerdictsOnlyOnceValidated(int version) {
        assertThat(versions.forShareClass(anyClass()).orElseThrow().version()).isNotEqualTo(version);
        theValidatorChoosesToFollowTheProspectus();
        acceptsOrCorrectsEveryOtherRule();
        validation.validate(anyClass(), version, VALIDATOR);
        assertDecides(version);
    }

    @Then("version {int} is unchanged")
    public void versionIsUnchanged(int version) {
        assertThat(versions.version(anyClass(), version)).contains(firstVersion);
    }

    @When("the validator marks the {string} clause Noted")
    public void theValidatorMarksTheClauseNoted(String clause) {
        if (draft == null) {
            theDraftRuleCardOf("Nordea 1 Global Climate and Environment");
        }
        int index = clause(clause);
        draft = validation.correct(anyClass(), draft.version(), index, new UnmodelledRestriction(
                UnmodelledRestriction.Status.NOTED, Optional.of("No condition on the client")), VALIDATOR);
    }

    @Then("the card's history records the marking, who made it and when")
    public void theCardsHistoryRecordsTheMarking() {
        RuleCard stored = versions.version(anyClass(), draft.version()).orElseThrow();
        assertThat(stored.rules().get(clause("reject or close")).history().getLast()).isEqualTo(new Revision(
                Revision.Action.EDITED, VALIDATOR, NOW,
                new UnmodelledRestriction(UnmodelledRestriction.Status.NOTED, Optional.of("No condition on the client"))));
    }

    /** The class's card is the one the engine reads, and a check on it is decided, not "Rule Card not validated". */
    private void assertDecides(int version) {
        assertThat(versions.forShareClass(anyClass()).orElseThrow().version()).isEqualTo(version);
        ClientProfile profile = clientProfiles.clientProfile(PORTFOLIO);
        EligibilityResult result = new FixedRuleEngine(versions, fundDocuments, fundRegistrations, fxRates)
                .check(new SubscriptionRequest(profile.portfolioId(), anyClass(),
                        Optional.of(new SubscriptionRequest.Money(new BigDecimal("80000"), "EUR")),
                        SubscriptionRequest.ClientInitiated.UNKNOWN), profile);
        assertThat(result.justification().notices()).doesNotContain("Rule Card not validated");
    }

    private CardReview review() {
        assertThat(draft).as("the Draft the validator works on").isNotNull();
        return validation.review(anyClass(), draft.version());
    }

    /** GCE-BP's ISIN: any class of the card names its fund. */
    private Isin anyClass() {
        return AnsweringSteps.shareClass("GCE-BP");
    }

    /** "reject or close" is quoted as "Reject or cancel any application ..." on p.138 of the prospectus. */
    private int clause(String clause) {
        assertThat(clause).isEqualTo("reject or close");
        List<Rule> rules = versions.version(anyClass(), draft.version()).orElseThrow().rules();
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).condition() instanceof UnmodelledRestriction && rules.get(index).citations().stream()
                    .anyMatch(citation -> citation.quote().startsWith("Reject or cancel any application"))) {
                return index;
            }
        }
        throw new AssertionError("No Unmodelled Restriction quoting the \"" + clause + "\" clause");
    }

    private Set<FundDocument.Type> documentTypes(CardReview.Contradiction contradiction) {
        return contradiction.rules().stream().map(draft.rules()::get).flatMap(rule -> documentTypes(rule).stream())
                .collect(Collectors.toSet());
    }

    private Set<FundDocument.Type> documentTypes(Rule rule) {
        Set<DocumentId> cited = rule.citations().stream().map(citation -> citation.document())
                .collect(Collectors.toSet());
        return fundDocuments.documents(anyClass()).stream()
                .filter(document -> cited.contains(document.documentId()))
                .flatMap(document -> document.type().stream())
                .collect(Collectors.toSet());
    }

    private DocumentId prospectusOf(RuleCard card) {
        return fundDocuments.documents(card.shareClasses().getFirst()).stream()
                .filter(document -> document.type().equals(Optional.of(FundDocument.Type.PROSPECTUS)))
                .map(FundDocument::documentId)
                .findFirst()
                .orElseThrow();
    }

    /** The demo's Draft card, as the pipeline drafts it (fixtures/draft-rule-cards). */
    private RuleCard nordeaDraft(String fund) {
        assertThat(fund.toLowerCase(Locale.ROOT)).isEqualTo("nordea 1 global climate and environment");
        return files.read(Path.of(fixtures, "draft-rule-cards")).stream()
                .filter(card -> card.shareClasses().contains(anyClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Draft Rule Card fixture of " + fund));
    }
}
