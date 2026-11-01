package com.fundgate.rulecard.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.rulecard.RuleCards;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.Isin;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FixtureRuleCardsTest {

    private static final Isin BNP_A = new Isin("CH0112537516");

    @Test
    void every_kind_on_a_card_is_read_through_the_catalog() {
        RuleCards cards = new FixtureRuleCards(Path.of("../docs/playbook/contracts/fixtures/rule-cards"),
                RuleCatalog.conditionTypes());

        assertThat(cards.forShareClass(BNP_A).orElseThrow().rules()).isNotEmpty();
    }

    @Test
    void a_condition_field_outside_the_contract_is_refused_not_ignored() {
        // "excludesUsCitizen": a typo that would otherwise leave US citizens allowed
        RuleCards withATypo = new FixtureRuleCards(Path.of("src/test/resources/rule-cards-outside-the-contract"),
                RuleCatalog.conditionTypes());

        assertThatThrownBy(() -> withATypo.forShareClass(BNP_A))
                .isInstanceOf(FundDataUnavailable.class)
                .rootCause().hasMessageContaining("excludesUsCitizen");
    }
}
