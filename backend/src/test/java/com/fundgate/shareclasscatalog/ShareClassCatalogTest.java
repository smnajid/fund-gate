package com.fundgate.shareclasscatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundgate.bankdata.FundDataUnavailable;
import com.fundgate.shareclasscatalog.Resolution.Ambiguous;
import com.fundgate.shareclasscatalog.Resolution.CandidateFund;
import com.fundgate.shareclasscatalog.Resolution.NotFound;
import com.fundgate.shareclasscatalog.Resolution.Resolved;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private static final String SL_AMCAP = "XXSLAMCAP001";
    private static final String SL_IA1 = "XXSLIA100001";
    private static final String NOT_COVERED_A = "XXNOTCOV0001";
    private static final String NOT_COVERED_B = "XXNOTCOV0002";

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
        assertThat(isinsOf(ShareClassMatcher.match(search, catalog.all()))).containsExactly(isins.split(" "));
    }

    /** No class here has a fund id (hypotheses.md, FMD fields): the fund name every class carries is the fan-out. */
    @Test
    void a_fund_named_without_a_class_fans_out_to_all_its_classes() {
        assertThat(resolvedIsins("Nordea 1 Global Climate and Environment")).containsExactly(GCE_BP, GCE_BI);
    }

    @Test
    void a_fund_and_class_mention_gives_that_class_only() {
        assertThat(resolvedIsins("Nordea Global Climate BI-EUR")).containsExactly(GCE_BI);
        assertThat(resolvedIsins("Swiss Life I Cap")).containsExactly(SL_ICAP);
        assertThat(resolvedIsins("Swiss Life I-A 1")).containsExactly(SL_IA1);
    }

    @Test
    void a_class_named_in_full_is_not_widened_to_a_class_whose_name_contains_it() {
        assertThat(resolvedIsins("BNP Paribas Bonds CHF A")).containsExactly(BNP_A);
        assertThat(resolvedIsins("BNP Paribas Bonds CHF A AC")).containsExactly(BNP_AAC);
        assertThat(resolvedIsins("BNP Paribas Bonds CHF I")).containsExactly(BNP_I);
    }

    /** A word of the fund's own name never names a class: "Bond Fund I" is the fund, not its class I. */
    @Test
    void a_fund_name_word_that_equals_a_class_name_does_not_name_that_class() {
        ShareClassCatalog bondFund = new ShareClassCatalog(() -> List.of(
                shareClass("XXSLBFI00001", Optional.empty(), "Swiss Life Funds (LUX) Bond Fund I", "I"),
                shareClass("XXSLBFA00001", Optional.empty(), "Swiss Life Funds (LUX) Bond Fund I", "A"),
                shareClass("XXSLBFP00001", Optional.empty(), "Swiss Life Funds (LUX) Bond Fund I", "P Cap")));

        assertThat(isinsOf(resolved(bondFund.resolve("Swiss Life Funds Bond Fund I"))))
                .containsExactly("XXSLBFI00001", "XXSLBFA00001", "XXSLBFP00001");
        assertThat(isinsOf(resolved(bondFund.resolve("Swiss Life Funds Bond Fund I P Cap"))))
                .containsExactly("XXSLBFP00001");
    }

    /** The "a" of "without a Rule Card" is a fund-name word, not class A's name, so neither fund loses a class. */
    @Test
    void a_fund_name_word_does_not_narrow_the_fixture_funds_to_class_a() {
        assertThat(ShareClassMatcher.match("Placeholder fund without a Rule Card", catalog.all()))
                .extracting(shareClass -> shareClass.isin().value())
                .containsExactly(NOT_COVERED_A, NOT_COVERED_B);
    }

    /** Several funds match: the dealer is asked for the ISIN or Valor, never handed classes of several funds. */
    @Test
    void a_mention_that_matches_several_funds_is_ambiguous_with_every_candidate_fund() {
        Ambiguous ambiguous = ambiguous(catalog.resolve("Nordea Global"));

        assertThat(ambiguous.funds()).extracting(CandidateFund::fundName).containsExactly(
                "Nordea 1 – Global Climate and Environment Fund", "Nordea SIF – Global Private Equity Fund");
        assertThat(ambiguous.funds()).extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of(GCE_BP, GCE_BI), List.of(SIF_W));
    }

    @Test
    void the_fixture_funds_without_a_rule_card_are_ambiguous_when_named_by_their_common_words() {
        Ambiguous ambiguous = ambiguous(catalog.resolve("Placeholder fund without a Rule Card"));

        assertThat(ambiguous.funds()).extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of(NOT_COVERED_A), List.of(NOT_COVERED_B));
    }

    /** The contract's fan-out key is the fund id when FMD has one; the fund name stands in only without it. */
    @Test
    void a_fund_id_when_present_decides_which_classes_are_one_fund() {
        ShareClassCatalog withFundIds = new ShareClassCatalog(() -> List.of(
                shareClass("XXGBF0000001", Optional.of("F1"), "Global Bond Fund", "A"),
                shareClass("XXGBF0000002", Optional.of("F1"), "Global Bond Fund (renamed)", "B"),
                shareClass("XXGBF0000003", Optional.of("F2"), "Global Bond Fund", "C")));

        Ambiguous ambiguous = ambiguous(withFundIds.resolve("Global Bond Fund"));

        assertThat(ambiguous.funds()).extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of("XXGBF0000001", "XXGBF0000002"), List.of("XXGBF0000003"));
    }

    @Test
    void case_accents_and_punctuation_are_ignored() {
        assertThat(resolvedIsins("NORDÉA sif, global private-equity!")).containsExactly(SIF_W);
        assertThat(resolvedIsins("Nordea 1 – Global Climate and Environment Fund")).containsExactly(GCE_BP, GCE_BI);
    }

    @Test
    void an_isin_matches_exactly() {
        assertThat(resolvedIsins("LU0348927095")).containsExactly(GCE_BI);
        assertThat(resolvedIsins("lu0348927095")).containsExactly(GCE_BI);
    }

    @Test
    void a_mention_that_matches_nothing_gives_no_share_class() {
        assertThat(catalog.resolve("LU9999999999")).isInstanceOf(NotFound.class);
        assertThat(catalog.resolve("Nordea Global Climate X-USD")).isInstanceOf(NotFound.class);
        assertThat(catalog.resolve("Pictet Water")).isInstanceOf(NotFound.class);
    }

    @Test
    void a_blank_or_missing_mention_gives_no_share_class() {
        assertThat(catalog.resolve("")).isInstanceOf(NotFound.class);
        assertThat(catalog.resolve(" – ")).isInstanceOf(NotFound.class);
        assertThat(catalog.resolve(null)).isInstanceOf(NotFound.class);
        assertThat(ShareClassMatcher.match(null, catalog.all())).isEmpty();
    }

    @Test
    void a_single_character_is_not_read_as_an_isin_prefix() {
        assertThat(catalog.resolve("BNP C")).isInstanceOf(NotFound.class);
    }

    /** "LU" or "XXSL" is a country or a placeholder, not an identifier: a prefix needs 3 characters and a digit. */
    @Test
    void an_identifier_prefix_needs_three_characters_and_a_digit() {
        assertThat(catalog.resolve("Nordea LU")).isInstanceOf(NotFound.class);
        assertThat(catalog.resolve("Swiss Life XXSL")).isInstanceOf(NotFound.class);
        assertThat(resolvedIsins("Nordea 1 LU0")).containsExactly(GCE_BP, GCE_BI);
        assertThat(catalog.resolve("Swiss Life XXSLAM")).isInstanceOf(NotFound.class);
        assertThat(resolvedIsins("XXSLAMCAP0")).containsExactly(SL_AMCAP);
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

    /** An empty catalog would turn every check into "fund not covered" (contracts/share-classes.md). */
    @Test
    void an_empty_catalog_does_not_load() {
        assertThatThrownBy(() -> new ShareClassCatalog(List::of)).isInstanceOf(FundDataUnavailable.class);
    }

    private List<String> resolvedIsins(String mention) {
        return isinsOf(resolved(catalog.resolve(mention)));
    }

    private static List<ShareClass> resolved(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Resolved.class);
        return ((Resolved) resolution).shareClasses();
    }

    private static Ambiguous ambiguous(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Ambiguous.class);
        return (Ambiguous) resolution;
    }

    private static ShareClass shareClass(String isin, Optional<String> fundId, String fundName, String className) {
        return new ShareClass(new Isin(isin), fundId, fundName, Optional.empty(), className, "CHF", Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH);
    }

    private static List<String> isinsOf(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(shareClass -> shareClass.isin().value()).toList();
    }
}
