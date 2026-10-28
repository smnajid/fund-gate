package com.fundgate.specs.requestparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fundgate.answering.Answer;
import com.fundgate.clientprofiles.PortfolioReference;
import com.fundgate.eligibility.SubscriptionRequest.Money;
import com.fundgate.eligibility.Verdict;
import com.fundgate.shareclasses.Isin;
import com.fundgate.specs.AnsweringSteps;
import com.fundgate.specs.ruleengine.RuleEngineSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/** The Subscription Requests read from an email: one Eligibility Check per portfolio and Share Class. */
public class RequestParserSteps {

    @Autowired
    private AnsweringSteps answering;

    private record CheckRun(PortfolioReference portfolio, Isin shareClass, Optional<Money> amount, Verdict verdict) {
    }

    @Then("these Eligibility Checks run:")
    public void theseEligibilityChecksRun(DataTable checks) {
        List<CheckRun> expected = checks.asMaps().stream()
                .map(row -> new CheckRun(new PortfolioReference(row.get("portfolio")),
                        AnsweringSteps.shareClass(row.get("Share Class")),
                        Optional.of(AnsweringSteps.money(row.get("amount"))),
                        RuleEngineSteps.verdict(row.get("Verdict"))))
                .toList();
        assertThat(answering.answered().checks().stream().map(RequestParserSteps::run).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Then("^the Eligibility Check of (\\w+) on ([\\w-]+) has no amount$")
    public void theEligibilityCheckHasNoAmount(String ref, String shareClass) {
        Isin isin = AnsweringSteps.shareClass(shareClass);
        assertThat(answering.answered().checks())
                .filteredOn(check -> check.portfolioRef().value().equals(ref)
                        && check.request().shareClass().equals(isin))
                .as("the Eligibility Check of %s on %s", ref, shareClass)
                .singleElement()
                .satisfies(check -> assertThat(check.request().amount()).isEmpty());
    }

    @Then("in the parsed request form these fields are editable:")
    public void inTheParsedRequestFormTheseFieldsAreEditable(DataTable fields) {
        Map<String, String> editable = fields.asMap();
        fail("The parsed request form is not built yet; expected editable: %s", editable);
    }

    private static CheckRun run(Answer.Check check) {
        Optional<Money> amount = check.request().amount()
                .map(money -> new Money(money.amount().stripTrailingZeros(), money.currency()));
        return new CheckRun(check.portfolioRef(), check.request().shareClass(), amount, check.result().verdict());
    }
}
