package com.fundgate.shareclasscatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A fund or class mention, or an ISIN, resolves to the Share Classes it names (architecture.md, Share Class catalog;
 * contracts/share-classes.md), over the platform's frozen copy of the catalog fixture, never the demo content the team
 * rewrites (#80). The short names are demo-dataset.md's.
 */
class ShareClassCatalogTest {

    private static final String GCE_BP = "LU0348926287";
    private static final String GCE_BI = "LU0348927095";
    private static final String SIF_W = "LU2607415275";
    private static final String BNP_A = "CH0112537516";
    private static final String BNP_AAC = "CH0114292532";
    private static final String BNP_I = "CH0112537557";
    private static final String SL_ICAP = "XXSLICAP0001";
    private static final String SL_IA1 = "XXSLIA100001";

    private final ShareClassCatalog catalog = new ShareClassCatalog(
            new FixtureShareClasses(Path.of("src/test/resources/fixtures/share-classes.json")));

    /** The search examples of 04-find-what-a-portfolio-can-buy.feature, on the matcher alone. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            climate nordea BI    | LU0348927095
            LU03489              | LU0348926287 LU0348927095
            bnp chf              | CH0112537516 CH0114292532 CH0112537557
            Bnp-Paribas  Bonds   | CH0112537516 CH0114292532 CH0112537557
            """)
    void the_search_examples_of_the_reverse_lookup(String search, String isins) {
        assertThat(isinsOf(catalog.find(search))).containsExactly(isins.split(" "));
    }

    /** No class here has a fund id (hypotheses.md, FMD fields): the fund name every class carries is the fan-out. */
    @Test
    void a_fund_named_without_a_class_fans_out_to_all_its_classes() {
        assertThat(isinsOf(catalog.find("Nordea 1 Global Climate and Environment"))).containsExactly(GCE_BP, GCE_BI);
    }

    @Test
    void a_fund_and_class_mention_gives_that_class_only() {
        assertThat(isinsOf(catalog.find("Nordea Global Climate BI-EUR"))).containsExactly(GCE_BI);
        assertThat(isinsOf(catalog.find("Swiss Life I Cap"))).containsExactly(SL_ICAP);
        assertThat(isinsOf(catalog.find("Swiss Life I-A 1"))).containsExactly(SL_IA1);
    }

    @Test
    void a_class_named_in_full_is_not_widened_to_a_class_whose_name_contains_it() {
        assertThat(isinsOf(catalog.find("BNP Paribas Bonds CHF A"))).containsExactly(BNP_A);
        assertThat(isinsOf(catalog.find("BNP Paribas Bonds CHF A AC"))).containsExactly(BNP_AAC);
        assertThat(isinsOf(catalog.find("BNP Paribas Bonds CHF I"))).containsExactly(BNP_I);
    }

    @Test
    void case_accents_and_punctuation_are_ignored() {
        assertThat(isinsOf(catalog.find("NORDÉA sif, global private-equity!"))).containsExactly(SIF_W);
        assertThat(isinsOf(catalog.find("Nordea 1 – Global Climate and Environment Fund"))).containsExactly(GCE_BP, GCE_BI);
    }

    @Test
    void an_isin_matches_exactly() {
        assertThat(isinsOf(catalog.find("LU0348927095"))).containsExactly(GCE_BI);
        assertThat(isinsOf(catalog.find("lu0348927095"))).containsExactly(GCE_BI);
    }

    @Test
    void a_mention_that_matches_nothing_gives_no_share_class() {
        assertThat(catalog.find("LU9999999999")).isEmpty();
        assertThat(catalog.find("Nordea Global Climate X-USD")).isEmpty();
        assertThat(catalog.find("Pictet Water")).isEmpty();
    }

    @Test
    void a_blank_mention_gives_no_share_class() {
        assertThat(catalog.find("")).isEmpty();
        assertThat(catalog.find(" – ")).isEmpty();
    }

    @Test
    void a_single_character_is_not_read_as_an_isin_prefix() {
        assertThat(isinsOf(catalog.find("BNP C"))).isEmpty();
    }

    @Test
    void the_catalog_is_read_once_and_kept_in_order() {
        assertThat(catalog.all()).hasSize(11);
        assertThat(catalog.all().getFirst().isin().value()).isEqualTo(GCE_BP);
    }

    @Test
    void a_catalog_that_cannot_be_read_does_not_load() {
        assertThatThrownBy(() -> new ShareClassCatalog(new FixtureShareClasses(Path.of("no-such-file.json"))))
                .isInstanceOf(FundDataUnavailable.class);
    }

    private static List<String> isinsOf(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(shareClass -> shareClass.isin().value()).toList();
    }
}
