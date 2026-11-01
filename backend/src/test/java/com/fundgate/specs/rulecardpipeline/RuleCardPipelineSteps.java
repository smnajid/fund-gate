package com.fundgate.specs.rulecardpipeline;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.fixture.FixtureRuleCards;
import com.fundgate.rulecardpipeline.Drafted;
import com.fundgate.rulecardpipeline.RuleCardPipeline;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.ruleengine.UnmodelledRestrictionKind.UnmodelledRestriction;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClasses;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.specs.RecordingLlmEgress;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** The Rule Card pipeline on the sample funds' public PDFs, through the replay egress client (06-validate-a-rule-card.feature). */
public class RuleCardPipelineSteps {

    @Autowired
    private FundDocuments fundDocuments;

    @Autowired
    private ShareClasses shareClasses;

    @Autowired
    private RecordingLlmEgress llm;

    @Value("${fundeli5.fixtures.dir}")
    private String fixtures;

    @Value("${fundeli5.pipeline.model}")
    private String model;

    private FundDocuments drawnFrom;
    private Drafted drafted;

    @When("^the pipeline drafts the Rule Card of (.+)$")
    public void thePipelineDraftsTheRuleCardOf(String fund) {
        drafted = pipeline(fundDocuments).draft(AnsweringSteps.shareClass(firstClassOf(fund)));
    }

    @Then("the Draft Rule Card has a rule for each rule kind the prospectus and KID state")
    public void theDraftRuleCardHasARuleForEachRuleKindTheProspectusAndKidState() {
        RuleCard card = draft().card();
        // The validated card of the same fund says which kinds its documents state
        RuleCard validated = new FixtureRuleCards(Path.of(fixtures, "rule-cards"), RuleCatalog.conditionTypes())
                .forShareClass(card.shareClasses().getFirst())
                .orElseThrow(() -> new AssertionError("No validated Rule Card fixture for " + card.fundName()));
        assertThat(card.status()).isEqualTo(RuleCard.Status.DRAFT);
        assertThat(kinds(card)).containsAll(kinds(validated));
    }

    @Then("every rule carries a quote found on its cited page")
    public void everyRuleCarriesAQuoteFoundOnItsCitedPage() {
        Drafted.Draft draft = draft();
        assertThat(draft.rulesWithoutQuote()).as("rules without a quote").isEmpty();
        assertThat(draft.quotesNotFound()).as("quotes not found on their cited page").isEmpty();
    }

    @Then("the pipeline read no Client Profile")
    public void thePipelineReadNoClientProfile() {
        // No port to one: the pipeline cannot read a Client Profile, whatever it is asked (ADR 0004)
        noClasses().that().resideInAPackage("com.fundgate.rulecardpipeline..")
                .should().dependOnClassesThat().resideInAPackage("com.fundgate.clientprofiles..")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.fundgate.rulecardpipeline"));
        assertThat(llm.sent()).allSatisfy(request -> assertThat(Stream.of(request.system(), request.prompt()))
                .noneMatch(text -> PortfolioReference.FORMAT.matcher(text).find()));
    }

    @Given("a fund document whose metadata has no content hash")
    public void aFundDocumentWhoseMetadataHasNoContentHash() {
        FundDocument prospectus = fundDocuments.documents(AnsweringSteps.shareClass("GCE-BP")).stream()
                .filter(document -> document.type().equals(Optional.of(FundDocument.Type.PROSPECTUS)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No prospectus for GCE-BP in the documents fixture"));
        FundDocument noHash = new FundDocument(prospectus.documentId(), prospectus.isins(), prospectus.type(),
                prospectus.language(), prospectus.fmdEditionDate(), prospectus.pdfEditionDate(),
                prospectus.confidentiality(), "", prospectus.sourceUrl(), prospectus.file());
        drawnFrom = new FundDocuments() {
            @Override
            public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
                return List.of(noHash);
            }

            @Override
            public byte[] content(DocumentId id) {
                return fundDocuments.content(id);
            }
        };
    }

    @When("the pipeline is asked to draft from it")
    public void thePipelineIsAskedToDraftFromIt() {
        drafted = pipeline(drawnFrom).draft(AnsweringSteps.shareClass("GCE-BP"));
    }

    @Then("no Rule Card is drafted")
    public void noRuleCardIsDrafted() {
        assertThat(drafted).isInstanceOf(Drafted.Refused.class);
        assertThat(llm.sent()).as("requests sent to the model").isEmpty();
    }

    @Then("the pipeline says why")
    public void thePipelineSaysWhy() {
        assertThat(drafted).isInstanceOfSatisfying(Drafted.Refused.class,
                refused -> assertThat(refused.reason()).contains("content hash"));
    }

    @Then("the {string} clause is an Unmodelled Restriction marked Blocking")
    public void theClauseIsAnUnmodelledRestrictionMarkedBlocking(String clause) {
        // "reject or close" is cited as e.g. "Reject or cancel any application ..." and "Soft or hard close a fund"
        List<Pattern> words = Stream.of(clause.split("\\W+")).filter(word -> word.length() > 3)
                .map(word -> Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE))
                .toList();
        assertThat(draft().card().rules())
                .filteredOn(rule -> rule.condition() instanceof UnmodelledRestriction)
                .filteredOn(rule -> rule.citations().stream().anyMatch(citation -> words.stream()
                        .anyMatch(word -> word.matcher(citation.quote()).find())))
                .as("Unmodelled Restrictions citing the \"%s\" clause", clause)
                .isNotEmpty()
                .allSatisfy(rule -> assertThat(rule.condition())
                        .isEqualTo(new UnmodelledRestriction(UnmodelledRestriction.Status.BLOCKING, Optional.empty())));
    }

    private RuleCardPipeline pipeline(FundDocuments documents) {
        return new RuleCardPipeline(documents, shareClasses, llm, model, "replay", Clock.systemUTC());
    }

    private Drafted.Draft draft() {
        assertThat(drafted).as("what the pipeline drafted").isInstanceOf(Drafted.Draft.class);
        return (Drafted.Draft) drafted;
    }

    /** The demo's fund names, as the scenarios write them, to one of the fund's short Share Class names. */
    private static String firstClassOf(String fund) {
        return switch (fund) {
            case "Nordea 1 Global Climate and Environment" -> "GCE-BP";
            default -> throw new AssertionError("No sample fund named " + fund + " in this step");
        };
    }

    private static List<Class<? extends Condition>> kinds(RuleCard card) {
        return card.rules().stream().map(Rule::condition).<Class<? extends Condition>>map(Condition::getClass)
                .distinct().toList();
    }
}
