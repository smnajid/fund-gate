package com.fundgate.shareclasses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The ShareClasses port over the demo dataset's catalog (contracts/share-classes.md, demo-dataset.md). */
class FixtureShareClassesTest {

    private final ShareClasses shareClasses =
            new FixtureShareClasses(Path.of("../docs/playbook/contracts/fixtures/share-classes.json"));

    @Test
    void the_catalog_holds_the_nine_demo_classes_and_two_with_no_rule_card() {
        assertThat(shareClasses.all()).hasSize(11);
    }

    @Test
    void the_demo_classes_with_a_confirmed_isin_carry_their_class_name_and_currency() {
        List<ShareClass> catalog = shareClasses.all();

        assertThat(catalog)
                .extracting(shareClass -> shareClass.isin().value(), ShareClass::shareClassName, ShareClass::currency)
                .contains(
                        tuple("LU0348926287", "BP-EUR", "EUR"),
                        tuple("LU0348927095", "BI-EUR", "EUR"),
                        tuple("LU2607415275", "BW-SEK", "SEK"),
                        tuple("CH0112537516", "A", "CHF"),
                        tuple("CH0114292532", "A AC", "CHF"),
                        tuple("CH0112537557", "I", "CHF"));
    }

    @Test
    void the_private_equity_class_says_so_for_the_target_market() {
        ShareClass sifW = shareClasses.all().stream()
                .filter(shareClass -> shareClass.isin().value().equals("LU2607415275"))
                .findFirst().orElseThrow();

        assertThat(sifW.productType().orElseThrow().code()).isEqualTo("PRIVATE_EQUITY");
        assertThat(sifW.fundType()).contains(ShareClass.FundType.SIF);
    }
}
