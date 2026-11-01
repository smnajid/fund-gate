package com.fundgate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.answering.AnswerRequests;
import com.fundgate.answering.RequestByHand;
import com.fundgate.checkrecord.CheckRecord;
import com.fundgate.checkrecord.CheckRecords;
import com.fundgate.checkrecord.memory.InMemoryCheckRecords;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.clientprofiles.fixture.FixtureClientProfiles;
import com.fundgate.eligibility.EligibilityResult;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.RuleOutcome.Outcome;
import com.fundgate.eligibility.SubscriptionRequest;
import com.fundgate.eligibility.SubscriptionRequest.ClientInitiated;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import com.fundgate.funddocuments.folder.FolderFundDocuments;
import com.fundgate.fundregistrations.fixture.FixtureFundRegistrations;
import com.fundgate.fxrates.fixture.FixtureFxRates;
import com.fundgate.privacygate.stub.StubPrivacyGate;
import com.fundgate.replydrafter.stub.StubReplyDrafter;
import com.fundgate.requestparser.stub.StubRequestParser;
import com.fundgate.rulecard.fixture.FixtureRuleCards;
import com.fundgate.ruleengine.FixedRuleEngine;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.ruleengine.RuleEngine;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The four promises, guarded on frozen test-only fixtures so no change of the demo content can break or weaken them
 * (#80, handover-to-the-team.md): no client data reaches an LLM, every check is recorded, no LLM decides a Verdict and
 * only a validated Rule Card decides, every rule outcome is cited.
 */
class FourPromisesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    private final FixtureClientProfiles clientProfiles = new FixtureClientProfiles(FIXTURES.resolve("client-profiles"));
    private final FixtureShareClasses shareClasses = new FixtureShareClasses(FIXTURES.resolve("share-classes.json"));
    private final RuleEngine engine = new FixedRuleEngine(
            new FixtureRuleCards(FIXTURES.resolve("rule-cards"), RuleCatalog.conditionTypes()),
            // A check reads only the manifest's editions, never a PDF, so the folder holds none
            new FolderFundDocuments(FIXTURES.resolve("fund-documents.json"), FIXTURES, shareClasses),
            new FixtureFundRegistrations(FIXTURES.resolve("fund-registrations.json")),
            new FixtureFxRates(FIXTURES.resolve("fx-rates.json")));
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T09:30:00Z"), ZoneOffset.UTC);

    /** ADR 0004: an email naming a portfolio the bank does not know is refused before any LLM call, and nothing is checked. */
    @Test
    void the_privacy_gate_fails_closed_so_an_unknown_portfolio_reaches_no_llm() {
        AtomicInteger llmCalls = new AtomicInteger();
        List<CheckRecord> written = new ArrayList<>();
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                email -> {
                    llmCalls.incrementAndGet();
                    return new StubRequestParser().parse(email);
                },
                engine,
                (email, facts) -> {
                    llmCalls.incrementAndGet();
                    return new StubReplyDrafter().draft(email, facts);
                },
                recording(written, new InMemoryCheckRecords()), clock);

        Answer answer = answering.answer("Hi desk, can LOC90000101 and LOC90009999 subscribe EUR 80,000 to "
                + "Nordea Global Climate BI-EUR?");

        assertThat(answer).isInstanceOfSatisfying(Answer.Refused.class,
                refused -> assertThat(refused.found()).containsExactly("LOC90009999"));
        assertThat(llmCalls).hasValue(0);
        assertThat(written).isEmpty();
    }

    /**
     * ADR 0004: what reaches the modules that call the LLM names portfolios by Portfolio Id only, never by Portfolio
     * Reference. (That the reply facts carry no Client Profile value is AnswerRequestsTest's.)
     */
    @Test
    void what_reaches_an_llm_names_portfolios_by_their_portfolio_id_only() {
        List<String> sent = new ArrayList<>();
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                email -> {
                    sent.add(email.toString());
                    return new StubRequestParser().parse(email);
                },
                engine,
                (email, facts) -> {
                    sent.add(email.toString());
                    sent.add(facts.toString());
                    return new StubReplyDrafter().draft(email, facts);
                },
                new InMemoryCheckRecords(), clock);

        Answer answer = answering.answer("Hi desk, can LOC90000101 and LOC90000102 subscribe EUR 80,000 to "
                + "Nordea Global Climate BI-EUR?");

        assertThat(answer).isInstanceOf(Answer.Answered.class);
        assertThat(sent).hasSize(3).noneMatch(text -> PortfolioReference.FORMAT.matcher(text).find());
        assertThat(sent.getFirst()).contains(portfolioId("LOC90000101"), portfolioId("LOC90000102"));
    }

    /** architecture.md, Audit: every check writes one Check Record of exactly what the Fund Dealer was shown. */
    @Test
    void every_eligibility_check_writes_one_check_record_with_the_verdict_shown() {
        CheckRecords checkRecords = new InMemoryCheckRecords();
        List<CheckRecord> written = new ArrayList<>();
        AnswerRequests answering = new AnswerRequests(clientProfiles, new StubPrivacyGate(clientProfiles),
                new StubRequestParser(), engine, new StubReplyDrafter(), recording(written, checkRecords), clock);

        Answer.Answered answer = answering.answerByHand(List.of(
                byHand("LOC90000101", "LU0348927095", "80000", "EUR"),
                byHand("LOC90000102", "LU0348927095", "80000", "EUR"),
                byHand("LOC90001201", "CH0112537516", "50000", "CHF")));

        assertThat(answer.checks()).hasSize(3).allSatisfy(check -> {
            CheckRecord record = checkRecords.find(check.checkId()).orElseThrow();
            assertThat(record.verdict()).isEqualTo(check.result().verdict());
            assertThat(record.justification()).isEqualTo(check.result().justification());
        });
        assertThat(written).hasSize(3);
    }

    /**
     * ADR 0002: fixed rules decide; any Fail gives Not Eligible, otherwise any Review gives Needs Review, otherwise
     * Eligible. A check with no rule outcome (a Draft card or none, a regime not covered) needs review.
     */
    @Test
    void any_fail_gives_not_eligible_otherwise_any_review_gives_needs_review_otherwise_eligible() {
        assertThat(check("LOC90000101", "LU0348927095", "80000", "EUR").verdict()).isEqualTo(Verdict.NOT_ELIGIBLE);
        assertThat(check("LOC90000102", "LU0348927095", "80000", "EUR").verdict()).isEqualTo(Verdict.ELIGIBLE);
        assertThat(check("LOC90001201", "CH0112537516", "50000", "CHF").verdict()).isEqualTo(Verdict.NEEDS_REVIEW);

        assertThat(everyCheck()).isNotEmpty().allSatisfy(result -> {
            List<Outcome> outcomes = result.justification().outcomes().stream().map(RuleOutcome::outcome).toList();
            Verdict expected = outcomes.contains(Outcome.FAIL) ? Verdict.NOT_ELIGIBLE
                    : outcomes.contains(Outcome.REVIEW) || outcomes.isEmpty() ? Verdict.NEEDS_REVIEW
                    : Verdict.ELIGIBLE;
            assertThat(result.verdict()).as(result.toString()).isEqualTo(expected);
        });
    }

    /** ADR 0001: a Draft Rule Card, or none, never decides; the check needs review. */
    @Test
    void only_a_validated_rule_card_decides() {
        EligibilityResult draft = check("LOC90001401", "CH0114292532", "50000", "CHF");
        EligibilityResult none = check("LOC90000101", "XXNOTCOV0001", "50000", "CHF");

        assertThat(List.of(draft, none)).allSatisfy(result -> {
            assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
            assertThat(result.justification().outcomes()).isEmpty();
        });
        assertThat(draft.justification().notices()).containsExactly("Rule Card not validated");
        assertThat(none.justification().notices()).containsExactly("Fund not covered");
    }

    /** GLOSSARY.md, Justification: each rule outcome is tied to a Citation. */
    @Test
    void every_rule_outcome_cites_what_it_relies_on() {
        assertThat(everyCheck().stream().flatMap(result -> result.justification().outcomes().stream()))
                .isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.citations()).as(outcome.toString()).isNotEmpty());
    }

    private EligibilityResult check(String portfolio, String isin, String amount, String currency) {
        PortfolioReference ref = new PortfolioReference(portfolio);
        return engine.check(new SubscriptionRequest(clientProfiles.portfolioId(ref).orElseThrow(), new Isin(isin),
                        Optional.of(new Money(new BigDecimal(amount), currency)), ClientInitiated.NO),
                clientProfiles.clientProfile(ref));
    }

    /** Every fixture portfolio against every catalog Share Class, with and without an amount, initiated or not. */
    private List<EligibilityResult> everyCheck() {
        List<Optional<Money>> amounts = List.of(Optional.empty(), Optional.of(new Money(new BigDecimal("1000"), "CHF")),
                Optional.of(new Money(new BigDecimal("80000"), "EUR")),
                Optional.of(new Money(new BigDecimal("2000000"), "CHF")));
        List<EligibilityResult> results = new ArrayList<>();
        for (PortfolioReference ref : portfolios()) {
            for (ShareClass shareClass : shareClasses.all()) {
                for (Optional<Money> amount : amounts) {
                    for (ClientInitiated initiated : ClientInitiated.values()) {
                        results.add(engine.check(new SubscriptionRequest(clientProfiles.portfolioId(ref).orElseThrow(),
                                shareClass.isin(), amount, initiated), clientProfiles.clientProfile(ref)));
                    }
                }
            }
        }
        return results;
    }

    private static List<PortfolioReference> portfolios() {
        try (Stream<Path> files = Files.list(FIXTURES.resolve("client-profiles"))) {
            return files.map(file -> file.getFileName().toString().replace(".json", "")).sorted()
                    .map(PortfolioReference::new).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RequestByHand byHand(String portfolio, String isin, String amount, String currency) {
        return new RequestByHand(new PortfolioReference(portfolio), new Isin(isin),
                Optional.of(new Money(new BigDecimal(amount), currency)), ClientInitiated.NO);
    }

    private String portfolioId(String portfolio) {
        return clientProfiles.portfolioId(new PortfolioReference(portfolio)).orElseThrow().toString();
    }

    /** Keeps every record written, and writes it on to the Check Records. */
    private static CheckRecords recording(List<CheckRecord> written, CheckRecords checkRecords) {
        return new CheckRecords() {
            @Override
            public void write(CheckRecord record) {
                written.add(record);
                checkRecords.write(record);
            }

            @Override
            public Optional<CheckRecord> find(UUID checkId) {
                return checkRecords.find(checkId);
            }
        };
    }
}
