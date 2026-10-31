package com.fundgate.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.rulecard.RuleKind;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Issue #79: every kind on the card is one plug-in, named as the card's JSON and the drafting prompt name it. */
class RuleCatalogTest {

    @Test
    void every_kind_on_the_card_has_exactly_one_plug_in() {
        assertThat(RuleCatalog.KINDS).extracting(RuleKindPlugin::kind)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(RuleKind.values())
                        .filter(kind -> kind != RuleKind.FUND_REGISTRATION)
                        .toList());
    }

    @Test
    void the_json_names_are_the_pipeline_s() {
        assertThat(RuleCatalog.conditionTypes()).containsOnlyKeys("investor_category", "us_person_exclusion",
                "minimum_initial_subscription", "mandate_only_class", "kid_required_for_retail",
                "unmodelled_restriction", "target_market");
    }

    @Test
    void each_plug_in_prompts_for_its_own_kind() {
        assertThat(RuleCatalog.KINDS)
                .allSatisfy(plugin -> assertThat(plugin.prompt()).startsWith("- " + plugin.name() + ": "));
    }

    @Test
    void only_unmodelled_restrictions_accumulate() {
        assertThat(RuleCatalog.KINDS).filteredOn(RuleKindPlugin::accumulates).extracting(RuleKindPlugin::kind)
                .containsExactly(RuleKind.UNMODELLED_RESTRICTION);
    }
}
