package com.fundgate.answering;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.clientprofiles.ClientProfiles;
import com.fundgate.clientprofiles.PortfolioId;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.clientprofiles.fixture.FixtureClientProfiles;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.privacygate.stub.StubPrivacyGate;
import com.fundgate.replydrafter.stub.StubReplyDrafter;
import com.fundgate.requestparser.stub.StubRequestParser;
import com.fundgate.ruleengine.stub.StubRuleEngine;
import com.fundgate.shareclasses.Isin;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    private Answer.Answered answered(String email) {
        return (Answer.Answered) answerRequests.answer(email);
    }

    private PortfolioId portfolioId(String ref) {
        return clientProfiles.portfolioId(new PortfolioReference(ref)).orElseThrow();
    }
}
