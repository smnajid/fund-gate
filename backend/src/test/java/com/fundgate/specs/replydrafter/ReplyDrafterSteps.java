package com.fundgate.specs.replydrafter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.citation.Citation;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.Verdict;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.specs.RecordingLlmEgress;
import com.fundgate.specs.ruleengine.RuleEngineSteps;
import io.cucumber.java.en.Then;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/** The reply, asserted by properties, never by wording: the LLM writes it (02-answer-a-subscription-request.feature). */
public class ReplyDrafterSteps {

    /** What gives a Verdict in a line of the reply, in English or French. */
    private static final Map<Verdict, Pattern> VERDICT_WORDS = Map.of(
            Verdict.ELIGIBLE, Pattern.compile("can subscribe|eligible|peut souscrire|éligible"),
            Verdict.NOT_ELIGIBLE, Pattern.compile("cannot subscribe|can't subscribe|ineligible|inéligible"
                    + "|not (yet |be )?eligible|n't eligible|non éligible|pas éligible|ne peut pas"),
            Verdict.NEEDS_REVIEW, Pattern.compile("review|vérifi|examen"));

    private static final List<String> FRENCH_WORDS = List.of("bonjour", "le", "la", "les", "est", "peut", "pour",
            "nous", "votre", "vous", "cordialement", "souscrire");

    private static final List<String> ENGLISH_WORDS = List.of("hello", "hi", "the", "is", "can", "for", "we", "your",
            "you", "regards", "subscribe");

    @Autowired
    private AnsweringSteps answering;

    @Autowired
    private RecordingLlmEgress llm;

    @Then("^the drafted reply names (\\w+) and (\\w+) and contains no Portfolio Id$")
    public void theDraftedReplyNamesAndContainsNoPortfolioId(String first, String second) {
        Answer.Answered answered = answering.answered();
        assertThat(answered.reply().text()).contains(first, second);
        assertThat(answered.reply().text()).doesNotContain(
                answered.swaps().stream().map(swap -> swap.portfolioId().toString()).toList());
    }

    @Then("the drafted reply gives each portfolio its Verdict")
    public void theDraftedReplyGivesEachPortfolioItsVerdict() {
        Answer.Answered answered = answering.answered();
        List<String> lines = answered.reply().text().toLowerCase(Locale.ROOT).lines().toList();
        assertThat(answered.checks()).allSatisfy(check -> assertThat(lines)
                .as("a line of the reply naming %s and giving %s", check.portfolioRef().value(), check.result().verdict())
                .anyMatch(line -> line.contains(check.portfolioRef().value().toLowerCase(Locale.ROOT))
                        && givesVerdict(line, check.result().verdict())));
    }

    @Then("^the drafted reply cites the (.+) rule for (\\w+), with its page$")
    public void theDraftedReplyCitesTheRuleWithItsPage(String rule, String ref) {
        RuleKind kind = RuleEngineSteps.ruleKind(rule);
        List<Integer> pages = checksOf(ref).stream()
                .flatMap(check -> check.result().justification().outcomes().stream())
                .filter(outcome -> outcome.rule() == kind)
                .map(RuleOutcome::citations)
                .flatMap(List::stream)
                .filter(Citation.Document.class::isInstance)
                .map(citation -> ((Citation.Document) citation).page())
                .toList();
        assertThat(pages).as("the pages the %s rule cites for %s", rule, ref).isNotEmpty();
        String reply = answering.answered().reply().text();
        assertThat(pages).anyMatch(page ->
                Pattern.compile("(?i)\\b(p\\.|page)\\s*" + page + "\\b").matcher(reply).find());
    }

    @Then("^the drafted reply suggests ([\\w-]+) for (\\w+)$")
    public void theDraftedReplySuggestsFor(String shareClass, String ref) {
        assertThat(answering.answered().reply().text()).contains(ref);
        theDraftedReplyRecommends(shareClass);
    }

    @Then("^the drafted reply recommends ([\\w-]+)$")
    public void theDraftedReplyRecommends(String shareClass) {
        String reply = answering.answered().reply().text();
        assertThat(List.of(AnsweringSteps.shareClass(shareClass).value(), AnsweringSteps.shareClassName(shareClass)))
                .as("%s by ISIN or class name in the reply", shareClass)
                .anyMatch(reply::contains);
    }

    @Then("the drafted reply is in French")
    public void theDraftedReplyIsInFrench() {
        Map<String, Long> words = Arrays.stream(answering.answered().reply().text().toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}]+"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long french = FRENCH_WORDS.stream().mapToLong(word -> words.getOrDefault(word, 0L)).sum();
        long english = ENGLISH_WORDS.stream().mapToLong(word -> words.getOrDefault(word, 0L)).sum();
        assertThat(french).as("French words against %d English ones", english).isGreaterThan(english);
    }

    @Then("the Verdicts are the same as with the LLM")
    public void theVerdictsAreTheSameAsWithTheLlm() {
        Map<String, Verdict> byHand = verdictsByPortfolio(answering.answered());
        Map<String, Verdict> withLlm = verdictsByPortfolio(answering.annasEmailAnsweredWithTheLlm());
        assertThat(byHand).isEqualTo(withLlm);
    }

    @Then("the reply is the template for each Verdict")
    public void theReplyIsTheTemplateForEachVerdict() {
        Answer.Answered answered = answering.answered();
        assertThat(answered.reply().draftedByAi()).isFalse();
        assertThat(answered.reply().text())
                .contains(answered.checks().stream().map(check -> check.portfolioRef().value()).toList());
    }

    @Then("^the reply is the template for (Eligible|Not Eligible|Needs Review), with no LLM call$")
    public void theReplyIsTheTemplateFor(String verdict) {
        Answer.Answered answered = answering.answered();
        assertThat(answered.checks()).extracting(check -> check.result().verdict())
                .containsOnly(RuleEngineSteps.verdict(verdict));
        assertThat(answered.reply().draftedByAi()).isFalse();
        assertThat(llm.sent()).isEmpty();
    }

    /** Eligible words are also inside their negations ("not eligible"), so an Eligible line must not be negated. */
    private static boolean givesVerdict(String line, Verdict verdict) {
        boolean negated = VERDICT_WORDS.get(Verdict.NOT_ELIGIBLE).matcher(line).find();
        return VERDICT_WORDS.get(verdict).matcher(line).find() && (verdict != Verdict.ELIGIBLE || !negated);
    }

    private List<Answer.Check> checksOf(String ref) {
        return answering.answered().checks().stream()
                .filter(check -> check.portfolioRef().value().equals(ref))
                .toList();
    }

    private static Map<String, Verdict> verdictsByPortfolio(Answer.Answered answered) {
        return answered.checks().stream()
                .collect(Collectors.toMap(check -> check.portfolioRef().value(), check -> check.result().verdict()));
    }
}
