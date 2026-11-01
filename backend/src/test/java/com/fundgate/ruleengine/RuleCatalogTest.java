package com.fundgate.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.RuleKind;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
    void the_kinds_come_in_the_drafting_prompt_s_order_unmodelled_restrictions_last() {
        // DraftPrompt (#65) lists them so, and the unmodelled line means "fits none of the kinds above"
        assertThat(RuleCatalog.KINDS).extracting(RuleKindPlugin::name).containsExactly("investor_category",
                "us_person_exclusion", "minimum_initial_subscription", "mandate_only_class",
                "kid_required_for_retail", "target_market", "unmodelled_restriction");
    }

    @Test
    void each_plug_in_prompts_for_its_own_kind() {
        assertThat(RuleCatalog.KINDS)
                .allSatisfy(plugin -> assertThat(plugin.prompt()).startsWith("- " + plugin.name() + ": "));
    }

    @Test
    void every_condition_belongs_to_a_plug_in() {
        JavaClasses main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fundgate");
        assertThat(main.stream()
                .filter(type -> type.isAssignableTo(Condition.class) && !type.isEquivalentTo(Condition.class))
                .map(JavaClass::getName))
                .containsExactlyInAnyOrderElementsOf(RuleCatalog.KINDS.stream()
                        .map(plugin -> plugin.condition().getName())
                        .toList());
    }

    @Test
    void only_unmodelled_restrictions_accumulate() {
        assertThat(RuleCatalog.KINDS).filteredOn(RuleKindPlugin::accumulates).extracting(RuleKindPlugin::kind)
                .containsExactly(RuleKind.UNMODELLED_RESTRICTION);
    }
}
