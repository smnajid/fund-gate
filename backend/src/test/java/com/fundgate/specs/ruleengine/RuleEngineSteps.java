package com.fundgate.specs.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.answering.Answer;
import com.fundgate.eligibility.RuleOutcome;
import com.fundgate.eligibility.Verdict;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.specs.AnsweringSteps;
import io.cucumber.java.en.Then;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/** The Verdict and rule outcomes of the one Eligibility Check a scenario ran. */
public class RuleEngineSteps {

    @Autowired
    private AnsweringSteps answering;

    @Then("^the Verdict is (Eligible|Not Eligible|Needs Review)$")
    public void theVerdictIs(String verdict) {
        assertThat(onlyCheck().result().verdict()).isEqualTo(verdict(verdict));
    }

    @Then("^the (.+) rule gives (Pass|Fail|Review|Warning)$")
    public void theRuleGives(String rule, String outcome) {
        RuleKind kind = ruleKind(rule);
        assertThat(onlyCheck().result().justification().outcomes())
                .filteredOn(ruleOutcome -> ruleOutcome.rule() == kind)
                .as("the outcome of the %s rule", rule)
                .singleElement()
                .extracting(RuleOutcome::outcome)
                .isEqualTo(RuleOutcome.Outcome.valueOf(outcome.toUpperCase()));
    }

    private Answer.Check onlyCheck() {
        assertThat(answering.answered().checks()).as("the Eligibility Checks run").hasSize(1);
        return answering.answered().checks().getFirst();
    }

    /** "Not Eligible" as NOT_ELIGIBLE. */
    public static Verdict verdict(String verdict) {
        return Verdict.valueOf(verdict.toUpperCase().replace(' ', '_'));
    }

    /** The rule as the specification names it, e.g. "mandate-only class" or "Unmodelled Restrictions". */
    public static RuleKind ruleKind(String rule) {
        String name = rule.toUpperCase().replaceAll("[ -]", "_");
        Optional<RuleKind> kind = Arrays.stream(RuleKind.values())
                .filter(candidate -> name.equals(candidate.name()) || name.equals(candidate.name() + "S"))
                .findFirst();
        assertThat(kind).as("a rule kind named %s", rule).isPresent();
        return kind.orElseThrow();
    }
}
