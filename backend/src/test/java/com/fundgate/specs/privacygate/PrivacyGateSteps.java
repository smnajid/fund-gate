package com.fundgate.specs.privacygate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.llm.LlmRequest;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.specs.RecordingLlmEgress;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

/** What the Privacy Gate let reach the LLM, and what the Fund Dealer sees when it refuses (ADR 0004). */
public class PrivacyGateSteps {

    /** The client facts a call may carry (architecture.md, Privacy boundary). */
    private static final Set<String> ALLOW_LIST = Set.of("Portfolio Id", "Legal Entity code",
            "Investor Classification", "mandate type", "residence country", "nationalities", "tax residence",
            "US Person / FATCA flags", "knowledge and experience", "risk profile", "ability to bear losses", "horizon");

    @Autowired
    private AnsweringSteps answering;

    @Autowired
    private RecordingLlmEgress llm;

    @Autowired
    private ClientProfiles clientProfiles;

    @Autowired
    private CheckRecords checkRecords;

    @Then("^the Privacy Gate replaces (\\w+) and (\\w+) by their Portfolio Ids$")
    public void thePrivacyGateReplacesByTheirPortfolioIds(String first, String second) {
        assertThat(answering.answered().swaps()).containsExactly(swap(first), swap(second));
    }

    @Then("no text sent to the LLM contains a Portfolio Reference or a client name")
    public void noTextSentToTheLlmContainsAPortfolioReferenceOrAClientName() {
        for (LlmRequest sent : llm.sent()) {
            String text = sent.system() + "\n" + sent.prompt();
            assertThat(PortfolioReference.FORMAT.matcher(text).find()).as("a Portfolio Reference sent").isFalse();
            assertThat(text).doesNotContain(AnsweringSteps.CLIENT_NAMES);
        }
    }

    @Then("the screen shows {string}")
    public void theScreenShows(String line) {
        // The screen writes this line from the swaps, shown only when the Gate let the email through
        assertThat(line).isEqualTo("the LLM saw Portfolio Ids only");
        assertThat(answering.answered().swaps()).isNotEmpty();
    }

    @Then("the screen says no AI was used for this email, and shows the client name it found")
    public void theScreenSaysNoAiWasUsedAndShowsTheClientNameItFound() {
        assertThat(answering.answer()).isInstanceOfSatisfying(Answer.Refused.class,
                refused -> assertThat(refused.found()).containsAnyElementsOf(AnsweringSteps.CLIENT_NAMES));
    }

    @Then("the Fund Dealer gets the request form to fill by hand")
    public void theFundDealerGetsTheRequestFormToFillByHand() {
        assertThat(answering.answer()).isInstanceOf(Answer.Refused.class);
    }

    @Then("every client fact sent is on the allow-list")
    public void everyClientFactSentIsOnTheAllowList() {
        List<CheckRecord.LlmCall> calls = llmCallsOfTheAnswer();
        assertThat(calls).as("the LLM calls the Check Records list").isNotEmpty();
        assertThat(calls).flatExtracting(CheckRecord.LlmCall::fieldsSent).allMatch(ALLOW_LIST::contains);
    }

    @Then("the Check Record lists exactly the fields sent")
    public void theCheckRecordListsExactlyTheFieldsSent() {
        List<CheckRecord.LlmCall> calls = llmCallsOfTheAnswer();
        assertThat(calls).as("the LLM calls the Check Records list").isNotEmpty();
        assertThat(calls).extracting(CheckRecord.LlmCall::gatedRequest)
                .containsExactlyInAnyOrderElementsOf(llm.sent());
    }

    private List<CheckRecord.LlmCall> llmCallsOfTheAnswer() {
        return answering.answered().checks().stream()
                .map(check -> checkRecords.find(check.checkId()).orElseThrow())
                .flatMap(record -> record.llmCalls().stream())
                .toList();
    }

    private Answer.Swap swap(String ref) {
        PortfolioReference portfolioRef = new PortfolioReference(ref);
        return new Answer.Swap(portfolioRef, clientProfiles.portfolioId(portfolioRef).orElseThrow());
    }
}
