package com.fundgate.specs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.answering.AnswerRequests;
import com.fundgate.answering.RequestByHand;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.shareclasses.Isin;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The steps every module shares when a Requester's email is answered: the demo dataset, the email, the Fund Dealer's
 * actions and what left for the LLM. Module step definitions read the answer from here.
 */
public class AnsweringSteps {

    public static final String ANNAS_EMAIL = """
            From: Marc Aebi (relationship manager)
            To: Fund dealing desk
            Subject: Subscription Nordea Global Climate - LOC90000101, LOC90000102

            Hi desk,
            The client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class,
            from portfolios LOC90000101 and LOC90000102. Can you confirm they can subscribe?
            Thanks, Marc
            """;

    static final String CLAIRES_EMAIL = """
            De : Sophie Lentz (conseillère)
            À : Fund dealing desk
            Objet : Souscription Nordea Global Climate - LUX90000301

            Bonjour,
            Ma cliente (LUX90000301) souhaite investir 20 000 EUR dans Nordea 1 Global Climate and Environment.
            Pouvez-vous valider ?
            Merci, Sophie
            """;

    static final String EMAIL_NAMING_ANNA = """
            From: Marc Aebi
            Subject: Nordea Global Climate for Anna Steinmann

            Hi desk, can Anna Steinmann (LOC90000101) subscribe EUR 80,000 to Nordea Global Climate BP-EUR?
            """;

    /** The demo clients' names (demo-dataset.md), full and surname alone: none may reach the LLM. */
    public static final List<String> CLIENT_NAMES = List.of("Anna Steinmann", "Claire Dupont", "Tom Whitfield",
            "Steinmann", "Dupont", "Whitfield");

    private static final Path DEMO_DATASET = Path.of("../docs/playbook/demo-dataset.md");

    @Autowired
    private AnswerRequests answerRequests;

    @Autowired
    private ClientProfiles clientProfiles;

    @Autowired
    private RecordingLlmEgress llm;

    private String email;

    private Answer answer;

    @Before
    public void startWithNothingSentToTheLlm() {
        llm.reset();
    }

    @Given("the demo dataset")
    public void theDemoDataset() {
        if (clientProfiles.portfolioId(new PortfolioReference("LOC90000101")).isEmpty()) {
            throw new IllegalStateException("The demo dataset's Client Profile fixtures are missing");
        }
    }

    @Given("the Validated Rule Cards of the sample funds")
    public void theValidatedRuleCardsOfTheSampleFunds() {
        // No Rule Card store yet: the stub Rule Engine reads none (#57)
    }

    @Given("the Requester's email:")
    public void theRequestersEmail(String email) {
        this.email = email;
    }

    @Given("Anna's email")
    public void annasEmail() {
        email = ANNAS_EMAIL;
    }

    @Given("Claire's email")
    public void clairesEmail() {
        email = CLAIRES_EMAIL;
    }

    @Given("Anna's email has been answered")
    public void annasEmailHasBeenAnswered() {
        email = ANNAS_EMAIL;
        answer = answerRequests.answer(email);
    }

    @Given("the Privacy Gate refused the email naming Anna Steinmann")
    public void thePrivacyGateRefusedTheEmailNamingAnna() {
        email = EMAIL_NAMING_ANNA;
        answer = answerRequests.answer(email);
        // The Gate's behaviour, so an assertion: it fails until the client name scan is built
        assertThat(answer).isInstanceOf(Answer.Refused.class);
    }

    @Given("the LLM egress client cannot reach the model")
    public void theLlmEgressClientCannotReachTheModel() {
        llm.reachable(false);
    }

    @When("the Fund Dealer asks FundEli5 to answer it")
    public void theFundDealerAsksFundEli5ToAnswerIt() {
        answer = answerRequests.answer(email);
    }

    @When("the Reply Drafter drafts the reply to Anna's email")
    public void theReplyDrafterDraftsTheReplyToAnnasEmail() {
        email = ANNAS_EMAIL;
        answer = answerRequests.answer(email);
    }

    @When("^the Fund Dealer enters (\\w+), ([\\w-]+), (\\w{3} [\\d,]+), (not )?client initiated$")
    public void theFundDealerEnters(String ref, String shareClass, String amount, String not) {
        answer = answerRequests.answerByHand(List.of(new RequestByHand(new PortfolioReference(ref),
                shareClass(shareClass), Optional.of(money(amount)),
                not == null ? ClientInitiated.YES : ClientInitiated.NO)));
    }

    @When("the Fund Dealer answers Anna's email entered by hand")
    public void theFundDealerAnswersAnnasEmailEnteredByHand() {
        answer = answerRequests.answerByHand(List.of("LOC90000101", "LOC90000102").stream()
                .map(ref -> new RequestByHand(new PortfolioReference(ref), shareClass("GCE-BI"),
                        Optional.of(money("EUR 80,000")), ClientInitiated.NO))
                .toList());
    }

    @When("^a structured Subscription Request arrives for (\\w+), ISIN (\\w+), (\\w{3} [\\d,]+)$")
    public void aStructuredSubscriptionRequestArrives(String ref, String isin, String amount) {
        answer = answerRequests.answerByHand(List.of(new RequestByHand(new PortfolioReference(ref), new Isin(isin),
                Optional.of(money(amount)), ClientInitiated.UNKNOWN)));
    }

    @Then("no LLM call is made")
    public void noLlmCallIsMade() {
        assertThat(llm.sent()).isEmpty();
    }

    /** What the last step answered, asserted to be an answer rather than a refusal. */
    public Answer.Answered answered() {
        assertThat(answer).as("the answer").isInstanceOf(Answer.Answered.class);
        return (Answer.Answered) answer;
    }

    public Answer answer() {
        assertThat(answer).as("the answer").isNotNull();
        return answer;
    }

    public String email() {
        return email;
    }

    /** Answers Anna's email again, with the model reachable, to compare against. */
    public Answer.Answered annasEmailAnsweredWithTheLlm() {
        llm.reachable(true);
        Answer withLlm = answerRequests.answer(ANNAS_EMAIL);
        assertThat(withLlm).isInstanceOf(Answer.Answered.class);
        return (Answer.Answered) withLlm;
    }

    /** A Share Class by its demo code, e.g. GCE-BI, from the Share Class table of demo-dataset.md. */
    public static Isin shareClass(String code) {
        return new Isin(demoShareClasses().get(code).isin());
    }

    /** The class name the demo dataset gives a code, e.g. BP-EUR for GCE-BP. */
    public static String shareClassName(String code) {
        return demoShareClasses().get(code).name();
    }

    /** "EUR 80,000" as Money, its amount without trailing zeros so that 80000 and 80000.00 compare equal. */
    public static Money money(String amount) {
        String[] currencyAndValue = amount.split(" ");
        return new Money(new BigDecimal(currencyAndValue[1].replace(",", "")).stripTrailingZeros(), currencyAndValue[0]);
    }

    private record DemoShareClass(String isin, String name) {
    }

    private static Map<String, DemoShareClass> demoShareClasses() {
        Pattern row = Pattern.compile("^\\| ([A-Z]+-[A-Z0-9]+) \\| [^|]*, ([^|,]+) \\| `([A-Z0-9]{12})` \\|");
        try {
            return Files.readAllLines(DEMO_DATASET).stream()
                    .map(row::matcher)
                    .filter(Matcher::find)
                    .collect(Collectors.toMap(found -> found.group(1),
                            found -> new DemoShareClass(found.group(3), found.group(2).strip()), (first, same) -> first));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
