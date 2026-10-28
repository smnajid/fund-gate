package com.fundgate.answering;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.citation.Citation;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.clientprofiles.fixture.FixtureClientProfiles;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.Justification;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.Verdict;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.privacygate.stub.StubPrivacyGate;
import com.fundgate.replydrafter.DraftedReply;
import com.fundgate.replydrafter.ReplyFacts;
import com.fundgate.requestparser.ParsedEmail;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.replydrafter.stub.StubReplyDrafter;
import com.fundgate.requestparser.stub.StubRequestParser;
import com.fundgate.ruleengine.stub.StubRuleEngine;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** A Requester's email answered through every module of the dealer app, each one a stub for now (issue #57). */
class AnswerRequestsTest {

    static final String ANNAS_EMAIL = """
            From: Marc Aebi (relationship manager)
            To: Fund dealing desk
            Subject: Subscription Nordea Global Climate - LOC90000101, LOC90000102

            Hi desk,
            The client would like to put EUR 80,000 into the Nordea Global Climate fund, institutional class,
            from portfolios LOC90000101 and LOC90000102. Can you confirm they can subscribe?
            Thanks, Marc
            """;

    private final ClientProfiles clientProfiles =
            new FixtureClientProfiles(Path.of("../docs/playbook/contracts/fixtures/client-profiles"));

    private final CheckRecords checkRecords = new InMemoryCheckRecords();

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T09:30:00Z"), ZoneOffset.UTC);

    private final AnswerRequests answerRequests = new AnswerRequests(clientProfiles,
            new StubPrivacyGate(clientProfiles), new StubRequestParser(), new StubRuleEngine(), new StubReplyDrafter(),
            checkRecords, clock);

    @Test
    void annas_email_gives_one_eligibility_check_per_portfolio_it_names() {
        Answer.Answered answer = answered(ANNAS_EMAIL);

        assertThat(answer.checks())
                .extracting(Answer.Check::portfolioRef)
                .containsExactly(new PortfolioReference("LOC90000101"), new PortfolioReference("LOC90000102"));
    }

    @Test
    void the_answer_shows_the_portfolio_id_the_privacy_gate_put_in_place_of_each_portfolio_reference() {
        Answer.Answered answer = answered(ANNAS_EMAIL);

        assertThat(answer.swaps()).containsExactly(
                new Answer.Swap(new PortfolioReference("LOC90000101"), portfolioId("LOC90000101")),
                new Answer.Swap(new PortfolioReference("LOC90000102"), portfolioId("LOC90000102")));
    }

    @Test
    void the_reply_names_each_portfolio_by_its_portfolio_reference_never_by_its_portfolio_id() {
        Answer.Answered answer = answered(ANNAS_EMAIL);

        assertThat(answer.reply().text()).contains("LOC90000101", "LOC90000102");
        assertThat(answer.reply().text()).doesNotContain(
                portfolioId("LOC90000101").toString(), portfolioId("LOC90000102").toString());
    }

    @Test
    void each_eligibility_check_leaves_a_check_record_of_what_the_dealer_was_shown() {
        Answer.Answered answer = answered(ANNAS_EMAIL);

        assertThat(answer.checks()).hasSize(2).allSatisfy(check -> {
            CheckRecord record = checkRecords.find(check.checkId()).orElseThrow();
            assertThat(record.portfolioRef()).isEqualTo(check.portfolioRef());
            assertThat(record.request()).isEqualTo(check.request());
            assertThat(record.verdict()).isEqualTo(check.result().verdict());
            assertThat(record.checkedAt()).isEqualTo(Instant.parse("2026-11-04T09:30:00Z"));
            assertThat(record.llmCalls()).isEmpty();
        });
    }

    @Test
    void an_email_with_a_portfolio_reference_the_bank_does_not_know_is_left_to_the_dealer_with_no_check_run() {
        Answer answer = answerRequests.answer(
                "Hi desk, can LOC90009999 subscribe EUR 80,000 to Nordea Global Climate BP-EUR?");

        assertThat(answer).isInstanceOfSatisfying(Answer.Refused.class,
                refused -> assertThat(refused.found()).containsExactly("LOC90009999"));
    }

    @Test
    void a_request_entered_by_hand_is_checked_and_answered_from_a_template() {
        Money eur80000 = new Money(new BigDecimal("80000"), "EUR");
        Answer.Answered answer = answerRequests.answerByHand(List.of(new RequestByHand(
                new PortfolioReference("LOC90000101"), new Isin("LU0348926287"), Optional.of(eur80000), ClientInitiated.NO)));

        assertThat(answer.checks()).singleElement().satisfies(check -> {
            assertThat(check.portfolioRef()).isEqualTo(new PortfolioReference("LOC90000101"));
            assertThat(check.request().portfolioId()).isEqualTo(portfolioId("LOC90000101"));
            assertThat(check.request().shareClass()).isEqualTo(new Isin("LU0348926287"));
            assertThat(check.request().amount()).contains(eur80000);
            assertThat(checkRecords.find(check.checkId())).isPresent();
        });
        assertThat(answer.reply().draftedByAi()).isFalse();
        assertThat(answer.reply().text()).contains("LOC90000101");
    }

    @Test
    void the_check_record_lists_every_llm_call_the_request_parser_and_the_reply_drafter_made() {
        CheckRecord.LlmCall parsing = llmCall("parse the request");
        CheckRecord.LlmCall drafting = llmCall("draft the reply");
        StubRequestParser stubParser = new StubRequestParser();
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                email -> new ParsedEmail(stubParser.parse(email).requests(), List.of(parsing)), new StubRuleEngine(),
                (email, facts) -> new DraftedReply("Hello", false, List.of(drafting)), checkRecords, clock);

        Answer.Answered answer = (Answer.Answered) answering.answer(ANNAS_EMAIL);

        assertThat(answer.checks()).allSatisfy(check -> assertThat(checkRecords.find(check.checkId()).orElseThrow()
                .llmCalls()).containsExactly(parsing, drafting));
    }

    @Test
    void the_reply_drafter_never_receives_a_client_profile_value() {
        AtomicReference<List<ReplyFacts>> drafted = new AtomicReference<>();
        RuleOutcome investorCategory = new RuleOutcome(RuleKind.INVESTOR_CATEGORY, RuleOutcome.Outcome.FAIL,
                "Not an institutional investor", List.of(
                        new Citation.Document(new DocumentId("prospectus"), 146, "institutional investors only",
                                Optional.empty()),
                        new Citation.ClientProfileField("client.birthDate", "1961-03-02")));
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                new StubRequestParser(),
                (request, profile) -> new EligibilityResult(Verdict.NOT_ELIGIBLE,
                        new Justification(List.of(investorCategory), List.of())),
                (email, facts) -> {
                    drafted.set(facts);
                    return new DraftedReply("Hello", false, List.of());
                }, checkRecords, clock);

        answering.answer(ANNAS_EMAIL);

        assertThat(drafted.get()).isNotEmpty().allSatisfy(facts -> {
            assertThat(facts.outcomes()).singleElement().satisfies(outcome -> assertThat(outcome.citations())
                    .containsExactly(new Citation.Document(new DocumentId("prospectus"), 146,
                            "institutional investors only", Optional.empty())));
            assertThat(facts.toString()).doesNotContain("1961-03-02");
        });
    }

    @Test
    void a_parsed_request_for_a_portfolio_the_email_does_not_name_is_refused_with_no_check_recorded() {
        PortfolioId elsewhere = new PortfolioId(UUID.fromString("00000000-0000-4000-8000-000000000000"));
        List<SubscriptionRequest> checked = new ArrayList<>();
        List<CheckRecord> written = new ArrayList<>();
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                email -> new ParsedEmail(List.of(new SubscriptionRequest(elsewhere, new Isin("LU0348927095"),
                        Optional.empty(), ClientInitiated.UNKNOWN)), List.of()),
                (request, profile) -> {
                    checked.add(request);
                    return new StubRuleEngine().check(request, profile);
                },
                new StubReplyDrafter(), new CheckRecords() {
                    @Override
                    public void write(CheckRecord record) {
                        written.add(record);
                    }

                    @Override
                    public Optional<CheckRecord> find(UUID checkId) {
                        return Optional.empty();
                    }
                }, clock);

        assertThat(answering.answer(ANNAS_EMAIL)).isInstanceOf(Answer.Refused.class);
        assertThat(checked).as("Eligibility Checks run").isEmpty();
        assertThat(written).as("Check Records written").isEmpty();
    }

    private CheckRecord.LlmCall llmCall(String purpose) {
        return new CheckRecord.LlmCall(purpose, new LlmRequest("model", "system", purpose),
                List.of("risk profile"), new LlmResponse("model", "answer"), Instant.parse("2026-11-04T09:30:00Z"));
    }

    private Answer.Answered answered(String email) {
        return (Answer.Answered) answerRequests.answer(email);
    }

    private PortfolioId portfolioId(String ref) {
        return clientProfiles.portfolioId(new PortfolioReference(ref)).orElseThrow();
    }
}
