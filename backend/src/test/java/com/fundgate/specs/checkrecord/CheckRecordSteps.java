package com.fundgate.specs.checkrecord;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.answering.AnswerRequests;
import com.fundgate.answering.RequestByHand;
import com.fundgate.checkrecord.AuditChecks;
import com.fundgate.checkrecord.AuditedCheck;
import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.DealerCorrection;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.llm.LlmRequest;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.specs.RecordingLlmEgress;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** What a Check Record holds, that it is never changed, and the Fund Dealer's corrections beside it (07-audit-a-check.feature). */
public class CheckRecordSteps {

    /** The value the walking skeleton writes where a version will be: it names no version (#57). */
    private static final String NO_VERSION = "stub";

    private static final String DEALER = "the Fund Dealer";

    @Autowired
    private AnsweringSteps answering;

    @Autowired
    private AnswerRequests answerRequests;

    @Autowired
    private CheckRecords checkRecords;

    @Autowired
    private AuditChecks auditChecks;

    @Autowired
    private RecordingLlmEgress llm;

    private RequestByHand checkedAgain;

    private CheckRecord firstRecord;

    private CheckRecord secondRecord;

    private UUID correctedCheck;

    private Instant correctionAsked;

    @Then("^two Check Records are written, one for (\\w+) and one for (\\w+)$")
    public void twoCheckRecordsAreWritten(String first, String second) {
        List<CheckRecord> records = recordsOfTheAnswer();
        assertThat(records).extracting(CheckRecord::portfolioRef)
                .containsExactlyInAnyOrder(new PortfolioReference(first), new PortfolioReference(second));
        assertThat(records).extracting(CheckRecord::checkId).doesNotHaveDuplicates();
    }

    @Then("each Check Record holds:")
    public void eachCheckRecordHolds(List<String> contents) {
        List<Answer.Check> checks = answering.answered().checks();
        assertThat(checks).as("the checks of the answer").isNotEmpty();
        for (Answer.Check check : checks) {
            CheckRecord record = recordOf(check.checkId());
            for (String content : contents) {
                assertHolds(record, check, content);
            }
        }
    }

    @Then("the Check Records hold, for each LLM call, the gated prompt, the client fields sent, the response and the model id")
    public void theCheckRecordsHoldEachLlmCall() {
        // Not vacuous: answering the email must have called the LLM
        assertThat(llm.sent()).as("the requests sent to the LLM").isNotEmpty();
        for (CheckRecord record : recordsOfTheAnswer()) {
            assertThat(record.llmCalls()).extracting(CheckRecord.LlmCall::gatedRequest)
                    .as("the gated requests the Check Record of %s holds", record.portfolioRef().value())
                    .containsExactlyInAnyOrderElementsOf(llm.sent());
            assertThat(record.llmCalls()).allSatisfy(call -> {
                assertThat(call.fieldsSent()).as("the client fields sent").isNotNull();
                assertThat(call.response()).as("the response").isNotNull();
                assertThat(call.response().text()).as("the response text").isNotNull();
                assertThat(call.response().model()).as("the model id").isNotBlank();
                assertThat(call.calledAt()).as("when the call was made").isNotNull();
            });
        }
    }

    @Then("no recorded prompt contains a Portfolio Reference or a client name")
    public void noRecordedPromptContainsAPortfolioReferenceOrAClientName() {
        List<LlmRequest> recorded = recordsOfTheAnswer().stream()
                .flatMap(record -> record.llmCalls().stream())
                .map(CheckRecord.LlmCall::gatedRequest)
                .toList();
        assertThat(recorded).as("the recorded LLM calls").isNotEmpty();
        for (LlmRequest request : recorded) {
            String text = request.system() + "\n" + request.prompt();
            assertThat(PortfolioReference.FORMAT.matcher(text).find()).as("a Portfolio Reference recorded").isFalse();
            assertThat(text).doesNotContainIgnoringCase(AnsweringSteps.CLIENT_NAMES.toArray(String[]::new));
        }
    }

    @When("the Privacy Gate refuses the email naming Anna Steinmann")
    public void thePrivacyGateRefusesTheEmailNamingAnna() {
        answering.thePrivacyGateRefusedTheEmailNamingAnna();
        // The refusal gives the request form: the Fund Dealer enters what the email asks
        answering.theFundDealerEntersByHand(new RequestByHand(new PortfolioReference("LOC90000101"),
                AnsweringSteps.shareClass("GCE-BP"), Optional.of(AnsweringSteps.money("EUR 80,000")),
                ClientInitiated.UNKNOWN));
    }

    @Then("the Check Record of the request entered by hand holds no LLM call")
    public void theCheckRecordOfTheRequestEnteredByHandHoldsNoLlmCall() {
        List<CheckRecord> records = recordsOfTheAnswer();
        assertThat(records).as("the Check Records of the request entered by hand").hasSize(1);
        assertThat(records.getFirst().llmCalls()).isEmpty();
        assertThat(llm.sent()).as("the requests sent to the LLM").isEmpty();
    }

    @Given("^a Check Record for (\\w+) on ([\\w-]+)$")
    public void aCheckRecordFor(String ref, String shareClass) {
        checkedAgain = new RequestByHand(new PortfolioReference(ref), AnsweringSteps.shareClass(shareClass),
                Optional.of(AnsweringSteps.money("EUR 80,000")), ClientInitiated.NO);
        firstRecord = recordOf(checkOnce(checkedAgain));
    }

    @When("the same check is run again")
    public void theSameCheckIsRunAgain() {
        secondRecord = recordOf(checkOnce(checkedAgain));
    }

    @Then("a second Check Record is written")
    public void aSecondCheckRecordIsWritten() {
        assertThat(secondRecord.checkId()).isNotEqualTo(firstRecord.checkId());
        assertThat(secondRecord.portfolioRef()).isEqualTo(firstRecord.portfolioRef());
        assertThat(secondRecord.request()).isEqualTo(firstRecord.request());
    }

    @Then("the first one is unchanged")
    public void theFirstOneIsUnchanged() {
        assertThat(checkRecords.find(firstRecord.checkId())).contains(firstRecord);
    }

    @When("^the Fund Dealer changes the currency of (\\w+)'s request from (\\w{3}) to (\\w{3})$")
    public void theFundDealerChangesTheCurrency(String ref, String parsed, String corrected) {
        correctedCheck = checkOf(ref).checkId();
        correctionAsked = Instant.now();
        auditChecks.correct(correctedCheck, DealerCorrection.Field.CURRENCY, corrected, DEALER);
    }

    @Then("^the Check Record keeps both the parsed currency (\\w{3}) and the dealer's (\\w{3}), with who and when$")
    public void theCheckRecordKeepsBothCurrencies(String parsed, String corrected) {
        AuditedCheck audited = auditChecks.audit(correctedCheck).orElseThrow();
        assertThat(audited.record().request().amount()).as("the parsed amount")
                .map(SubscriptionRequest.Money::currency).contains(parsed);
        assertThat(audited.corrections()).singleElement().satisfies(correction -> {
            assertThat(correction.field()).isEqualTo(DealerCorrection.Field.CURRENCY);
            assertThat(correction.parsed()).as("what the AI read").contains(parsed);
            assertThat(correction.corrected()).as("what the Fund Dealer entered").isEqualTo(corrected);
            assertThat(correction.dealer()).as("who").isEqualTo(DEALER);
            assertThat(correction.correctedAt()).as("when").isBetween(correctionAsked, Instant.now());
        });
    }

    private void assertHolds(CheckRecord record, Answer.Check check, String content) {
        switch (content) {
            case "the Subscription Request" -> assertThat(record.request()).isEqualTo(check.request());
            case "the Rule Card version" -> assertThat(record.ruleCard())
                    .as("the version of the Validated Rule Card the check read").isPresent();
            case "the Scale Mapping version" -> assertThat(record.scaleMappingVersion())
                    .as("the Scale Mapping version").isNotBlank().isNotEqualTo(NO_VERSION);
            case "the Client Profile fields the check read" -> {
                List<Citation.ClientProfileField> read = check.result().justification().outcomes().stream()
                        .flatMap(outcome -> outcome.citations().stream())
                        .filter(Citation.ClientProfileField.class::isInstance)
                        .map(Citation.ClientProfileField.class::cast)
                        .distinct()
                        .toList();
                assertThat(read).as("the Client Profile fields the Justification cites").isNotEmpty();
                assertThat(record.clientProfileSnapshot()).containsExactlyInAnyOrderElementsOf(read);
            }
            case "the engine version" -> assertThat(record.engineVersion())
                    .as("the engine version").isNotBlank().isNotEqualTo(NO_VERSION);
            case "the Verdict and the Justification" -> {
                assertThat(record.verdict()).isEqualTo(check.result().verdict());
                assertThat(record.justification()).isEqualTo(check.result().justification());
            }
            default -> throw new IllegalArgumentException("No Check Record content known as " + content);
        }
    }

    private UUID checkOnce(RequestByHand request) {
        return answerRequests.answerByHand(List.of(request)).checks().getFirst().checkId();
    }

    private Answer.Check checkOf(String ref) {
        return answering.answered().checks().stream()
                .filter(check -> check.portfolioRef().value().equals(ref))
                .findFirst().orElseThrow(() -> new AssertionError("No check of " + ref + " in the answer"));
    }

    private List<CheckRecord> recordsOfTheAnswer() {
        return answering.answered().checks().stream().map(check -> recordOf(check.checkId())).toList();
    }

    private CheckRecord recordOf(UUID checkId) {
        return checkRecords.find(checkId).orElseThrow(() -> new AssertionError("No Check Record " + checkId));
    }
}
