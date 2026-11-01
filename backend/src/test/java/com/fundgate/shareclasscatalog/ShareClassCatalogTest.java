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
import com.fundgate.shareclasses.ShareClass.FundType;
import com.fundgate.shareclasses.fixture.FixtureShareClasses;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /**
     * A class name typed beyond the fund name's own words outranks one that needs them: the second "CHF" of "Bonds CHF
     * A CHF" belongs to class A CHF, and the "I" of "Bond Fund I P Cap" to the fund, not to class I Cap.
     */
    @Test
    void a_class_word_that_is_also_a_fund_name_word_names_the_class_when_typed_again() {
        ShareClassCatalog bondsChf = new ShareClassCatalog(() -> List.of(
                shareClass("XXBC00000001", Optional.empty(), "Bonds CHF", "A"),
                shareClass("XXBC00000002", Optional.empty(), "Bonds CHF", "A CHF"),
                shareClass("XXBC00000003", Optional.empty(), "Bonds CHF", "CHF Hedged"),
                shareClass("XXBC00000004", Optional.empty(), "Bonds CHF", "EUR Hedged"),
                shareClass("XXBC00000005", Optional.empty(), "Bonds CHF", "Hedged")));
        ShareClassCatalog bondFund = new ShareClassCatalog(() -> List.of(
                shareClass("XXSLBFI00001", Optional.empty(), "Swiss Life Funds (LUX) Bond Fund I", "I Cap"),
                shareClass("XXSLBFP00001", Optional.empty(), "Swiss Life Funds (LUX) Bond Fund I", "P Cap")));
        ShareClassCatalog fundA = new ShareClassCatalog(() -> List.of(
                shareClass("XXFA00000001", Optional.empty(), "Fund A", "A"),
                shareClass("XXFA00000002", Optional.empty(), "Fund A", "A AC")));

        assertThat(isinsOf(resolved(bondsChf.resolve("Bonds CHF A CHF")))).containsExactly("XXBC00000002");
        assertThat(isinsOf(resolved(bondsChf.resolve("Bonds CHF CHF Hedged")))).containsExactly("XXBC00000003");
        assertThat(isinsOf(resolved(bondFund.resolve("Bond Fund I I Cap")))).containsExactly("XXSLBFI00001");
        assertThat(isinsOf(resolved(bondFund.resolve("Bond Fund I P Cap")))).containsExactly("XXSLBFP00001");
        assertThat(isinsOf(resolved(fundA.resolve("Fund A A AC")))).containsExactly("XXFA00000002");
    }

    /**
     * "Bonds CHF Hedged" may be fund Bonds CHF, class Hedged, or a shortened fund name and class CHF Hedged: the
     * dealer is asked for the ISIN or Valor, never handed the shorter class as if it were certain.
     */
    @Test
    void a_fund_name_word_that_may_complete_a_longer_class_name_leaves_the_class_ambiguous() {
        ShareClassCatalog bondsChf = new ShareClassCatalog(() -> List.of(
                shareClass("XXBC00000003", Optional.empty(), "Bonds CHF", "CHF Hedged"),
                shareClass("XXBC00000005", Optional.empty(), "Bonds CHF", "Hedged")));

        Ambiguous ambiguous = ambiguous(bondsChf.resolve("Bonds CHF Hedged"));

        assertThat(ambiguous.funds()).extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of("XXBC00000003", "XXBC00000005"));
    }

    /**
     * A word the fund or umbrella name uses never counts toward a class name, however the class name reads: the "1"
     * of "Nordea 1" (fund and umbrella name each use it once) is not the "1" of class I-A 1, the "I" of "Bond Fund I" is
     * not the "I" of class I Cap, the "CHF" of "Bonds CHF" is not the "CHF" of class CHF Hedged. The dealer is asked for
     * the ISIN or Valor.
     */
    @Test
    void a_class_name_that_needs_a_fund_or_umbrella_name_word_is_ambiguous() {
        ShareClassCatalog fundX = new ShareClassCatalog(() -> List.of(
                inUmbrella(shareClass("XXFX00000001", Optional.empty(), "Nordea 1 - Fund X", "I-A 1"), "Nordea 1, SICAV"),
                inUmbrella(shareClass("XXFX00000002", Optional.empty(), "Nordea 1 - Fund X", "I-A 2"), "Nordea 1, SICAV")));
        ShareClassCatalog bondFund = new ShareClassCatalog(() -> List.of(
                shareClass("XXBFI0000001", Optional.empty(), "Bond Fund I", "I Cap"),
                shareClass("XXBFP0000001", Optional.empty(), "Bond Fund I", "P Cap")));
        ShareClassCatalog bondsChf = new ShareClassCatalog(() -> List.of(
                shareClass("XXBC00000003", Optional.empty(), "Bonds CHF", "CHF Hedged"),
                shareClass("XXBC00000004", Optional.empty(), "Bonds CHF", "EUR Hedged")));

        assertThat(candidateIsins(fundX.resolve("Fund X I-A 1"))).containsExactly(List.of("XXFX00000001", "XXFX00000002"));
        assertThat(candidateIsins(fundX.resolve("Nordea 1 Fund X I-A 1")))
                .containsExactly(List.of("XXFX00000001", "XXFX00000002"));
        assertThat(candidateIsins(bondFund.resolve("Bond Fund I Cap")))
                .containsExactly(List.of("XXBFI0000001", "XXBFP0000001"));
        assertThat(candidateIsins(bondsChf.resolve("Bonds CHF Hedged")))
                .containsExactly(List.of("XXBC00000003", "XXBC00000004"));
        assertThat(isinsOf(resolved(fundX.resolve("Nordea 1 SICAV Nordea 1 Fund X I-A 1")))).containsExactly("XXFX00000001");
    }

    /**
     * A word every class of the fund carries, a shared currency or fund type, names no class: "Bond Fund I CHF" is
     * not the fund's classes, nor one of them, but the dealer's to settle. Neither is a word only one class carries that
     * is not a class name ("EUR" of the one class in EUR), nor a class name every class carries ("Cap" beside "I Cap").
     */
    @Test
    void a_word_beyond_the_fund_name_that_names_no_class_is_ambiguous() {
        ShareClassCatalog bondFund = new ShareClassCatalog(() -> List.of(
                inFundType(shareClass("XXSLBFI00001", Optional.empty(), "Bond Fund I", "I"), FundType.UCITS),
                inFundType(shareClass("XXSLBFA00001", Optional.empty(), "Bond Fund I", "A"), FundType.UCITS)));
        ShareClassCatalog inTwoCurrencies = new ShareClassCatalog(() -> List.of(
                inCurrency(shareClass("XXSLBFI00001", Optional.empty(), "Bond Fund I", "I"), "EUR"),
                shareClass("XXSLBFA00001", Optional.empty(), "Bond Fund I", "A")));
        ShareClassCatalog caps = new ShareClassCatalog(() -> List.of(
                shareClass("XXCAP0000001", Optional.empty(), "Bond Fund", "Cap"),
                shareClass("XXCAP0000002", Optional.empty(), "Bond Fund", "I Cap")));

        assertThat(candidateIsins(bondFund.resolve("Bond Fund I CHF")))
                .containsExactly(List.of("XXSLBFI00001", "XXSLBFA00001"));
        assertThat(candidateIsins(bondFund.resolve("Bond Fund I UCITS")))
                .containsExactly(List.of("XXSLBFI00001", "XXSLBFA00001"));
        assertThat(candidateIsins(inTwoCurrencies.resolve("Bond Fund I EUR"))).containsExactly(List.of("XXSLBFI00001"));
        assertThat(candidateIsins(caps.resolve("Bond Fund Cap"))).containsExactly(List.of("XXCAP0000001", "XXCAP0000002"));
        assertThat(isinsOf(resolved(caps.resolve("Bond Fund I Cap")))).containsExactly("XXCAP0000002");
    }

    /** "(USD)" names the fund here: the class needs its own "(USD)" typed beyond the fund name's two. */
    @Test
    void a_class_word_beyond_the_fund_name_never_fans_out_to_every_class() {
        ShareClassCatalog usdCorporates = new ShareClassCatalog(() -> List.of(
                shareClass("XXUBS0000001", Optional.empty(), "UBS Bond - USD Corporates (USD)", "(USD) P-acc"),
                shareClass("XXUBS0000002", Optional.empty(), "UBS Bond - USD Corporates (USD)", "(EUR hedged) P-acc")));

        assertThat(candidateIsins(usdCorporates.resolve("UBS Bond USD Corporates (USD) P-acc")))
                .containsExactly(List.of("XXUBS0000001", "XXUBS0000002"));
        assertThat(isinsOf(resolved(usdCorporates.resolve("UBS Bond USD Corporates (USD) (USD) P-acc"))))
                .containsExactly("XXUBS0000001");
    }

    /** A class name typed only in part is the dealer's to settle: the candidate classes, never a pick. */
    @Test
    void a_class_name_typed_in_part_is_ambiguous_with_the_candidate_classes() {
        ShareClassCatalog usdCorporates = new ShareClassCatalog(() -> List.of(
                shareClass("XXUBS0000003", Optional.empty(), "UBS Bond - USD Corporates", "(EUR hedged) P-acc"),
                shareClass("XXUBS0000004", Optional.empty(), "UBS Bond - USD Corporates", "(CHF hedged) P-acc"),
                shareClass("XXUBS0000005", Optional.empty(), "UBS Bond - USD Corporates", "I-B")));

        assertThat(ambiguous(catalog.resolve("Swiss Life I")).funds()).extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of(SL_ICAP, SL_IA1));
        assertThat(ambiguous(usdCorporates.resolve("UBS USD Corporates P-acc")).funds())
                .extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of("XXUBS0000003", "XXUBS0000004"));
    }

    /** A currency is not a class name: the one class it leaves is a candidate, not a pick. */
    @Test
    void a_class_picked_only_by_its_currency_is_ambiguous() {
        ShareClassCatalog globalEquity = new ShareClassCatalog(() -> List.of(
                inCurrency(shareClass("XXGE00000001", Optional.empty(), "Global Equity", "A"), "EUR"),
                inCurrency(shareClass("XXGE00000002", Optional.empty(), "Global Equity", "B"), "USD")));

        assertThat(ambiguous(globalEquity.resolve("Global Equity EUR")).funds())
                .extracting(fund -> isinsOf(fund.shareClasses()))
                .containsExactly(List.of("XXGE00000001"));
    }

    /**
     * Classes of several funds match: Ambiguous, one candidate per fund, even when the mention gives one fund's name in
     * full, since a shorter fund name may be another fund's name typed in part.
     */
    @Test
    void a_mention_that_gives_one_fund_name_in_full_is_still_ambiguous_when_another_fund_matches() {
        ShareClassCatalog globalBonds = new ShareClassCatalog(() -> List.of(
                shareClass("XXGB00000001", Optional.empty(), "Global Bond", "A"),
                shareClass("XXGB00000002", Optional.empty(), "Global Bond", "B"),
                shareClass("XXGBP0000001", Optional.empty(), "Global Bond Plus", "A")));

        assertThat(candidateIsins(globalBonds.resolve("Global Bond A")))
                .containsExactly(List.of("XXGB00000001"), List.of("XXGBP0000001"));
        assertThat(candidateIsins(catalog.resolve("Placeholder fund without a Rule Card A")))
                .containsExactly(List.of(NOT_COVERED_A), List.of(NOT_COVERED_B));
        assertThat(isinsOf(resolved(globalBonds.resolve("Global Bond Plus A")))).containsExactly("XXGBP0000001");
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

    /** A fund with a fund id is shown under the name of its first class in catalog order, whichever classes matched. */
    @Test
    void a_candidate_fund_with_a_fund_id_is_named_by_its_first_class_in_catalog_order() {
        ShareClassCatalog withFundIds = new ShareClassCatalog(() -> List.of(
                shareClass("XXGBF0000001", Optional.of("F1"), "Global Bond Fund", "A"),
                shareClass("XXGBF0000002", Optional.of("F1"), "Global Bond Fund (renamed)", "B"),
                shareClass("XXGBF0000003", Optional.of("F2"), "Global Bond Fund (renamed)", "C")));

        Ambiguous ambiguous = ambiguous(withFundIds.resolve("Global Bond Fund renamed"));

        assertThat(ambiguous.funds()).extracting(CandidateFund::fundName)
                .containsExactly("Global Bond Fund", "Global Bond Fund (renamed)");
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

    /** Valors are 6 to 9 digits: a shorter number is a word, never a Valor prefix that picks a class. */
    @Test
    void an_all_digit_valor_prefix_needs_six_digits() {
        ShareClassCatalog withValors = new ShareClassCatalog(() -> List.of(
                withValor(shareClass("XXBF00000001", Optional.empty(), "Bond Fund", "A"), "1234567"),
                withValor(shareClass("XXBF00000002", Optional.empty(), "Bond Fund", "B"), "7654321")));

        assertThat(withValors.resolve("Bond Fund 123")).isInstanceOf(NotFound.class);
        assertThat(isinsOf(resolved(withValors.resolve("Bond Fund 123456")))).containsExactly("XXBF00000001");
        assertThat(isinsOf(resolved(withValors.resolve("1234567")))).containsExactly("XXBF00000001");
    }

    /**
     * The rule of {@link Resolution.Resolved}, restated here, over small catalogs whose fund, umbrella and class names
     * share words, with fund ids (one fund's classes under different names), Valors, ISIN prefixes several funds share
     * and fund types, and mentions mixing those words, now and then lowercase (fixed seed, so the same cases every run).
     * Beside the rule, what it is for: a word every class carries never narrows the fund to one class, one class is
     * never Resolved without a word of its own name typed beyond the fund's, or its ISIN or Valor, and never two funds.
     */
    @Test
    void a_resolved_mention_names_the_fund_alone_or_one_class_in_full() {
        List<String> fundNames = List.of("Bond Fund I", "Bonds CHF", "Fund A", "Bond Fund", "Fund I-A 1", "CHF Bond",
                "Global Bond USD", "Bond 2 Fund");
        List<String> umbrellaNames = List.of("", "", "Fund 1", "Bond I", "USD Cap");
        List<String> classNames = List.of("I", "A", "I Cap", "P Cap", "CHF Hedged", "EUR Hedged", "Hedged", "P-acc",
                "(USD) P-acc", "I-A 1", "I-A 2", "A AC", "1", "A CHF", "EUR", "Cap");
        List<String> currencies = List.of("CHF", "EUR", "USD");
        List<Optional<FundType>> fundTypes = List.of(Optional.empty(), Optional.of(FundType.UCITS),
                Optional.of(FundType.SIF), Optional.of(FundType.PART_II), Optional.of(FundType.CH_SECURITIES_FUND));
        List<String> isinPrefixes = List.of("LU0348", "LU0349", "CH0112");
        List<String> mentionWords = List.of("I", "A", "CHF", "EUR", "USD", "Cap", "Hedged", "P-acc", "1", "2", "Bond",
                "Bonds", "Fund", "AC", "Global", "I-A", "P", "UCITS", "SIF", "Part", "II", "Securities");
        Random random = new Random(60);
        Map<String, Integer> outcomes = new TreeMap<>();
        int sharedWordChecks = 0;
        for (int catalogNo = 0; catalogNo < 1500; catalogNo++) {
            List<ShareClass> classes = new ArrayList<>();
            int funds = 1 + random.nextInt(2);
            for (int fund = 0; fund < funds; fund++) {
                String fundName = pick(random, fundNames);
                String umbrella = pick(random, umbrellaNames);
                Optional<String> fundId = random.nextInt(3) == 0 ? Optional.of("F" + fund) : Optional.empty();
                Optional<FundType> fundType = pick(random, fundTypes);
                int classCount = 1 + random.nextInt(3);
                for (int classNo = 0; classNo < classCount; classNo++) {
                    classes.add(new ShareClass(
                            new Isin(pick(random, isinPrefixes) + "%06d".formatted(catalogNo * 10 + fund * 4 + classNo)),
                            fundId, fundId.isPresent() && random.nextInt(3) == 0 ? pick(random, fundNames) : fundName,
                            umbrella.isEmpty() ? Optional.empty() : Optional.of(umbrella), pick(random, classNames),
                            pick(random, currencies), Optional.empty(), fundType, Optional.empty(),
                            random.nextInt(2) == 0 ? Optional.of(String.valueOf(1_234_000 + random.nextInt(200)))
                                    : Optional.empty(),
                            Optional.empty(), Instant.EPOCH));
                }
            }
            ShareClassCatalog generated = new ShareClassCatalog(() -> classes);
            for (int mentionNo = 0; mentionNo < 30; mentionNo++) {
                String mention = mention(random, classes, mentionWords);
                Resolution resolution = generated.resolve(mention);
                outcomes.merge(outcome(resolution), 1, Integer::sum);
                assertResolvedKeepsTheRule(mention, resolution, classes);
                sharedWordChecks += assertAWordEveryClassCarriesNeverPicksOne(mention, resolution, classes, generated);
            }
        }
        assertThat(outcomes.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(45_000);
        assertThat(outcomes).as("outcomes %s", outcomes)
                .hasEntrySatisfying("Resolved, one class", count -> assertThat(count).isGreaterThan(4_000))
                .hasEntrySatisfying("Resolved, several classes", count -> assertThat(count).isGreaterThan(2_000))
                .hasEntrySatisfying("Ambiguous", count -> assertThat(count).isGreaterThan(6_000))
                .hasEntrySatisfying("NotFound", count -> assertThat(count).isGreaterThan(1_000));
        assertThat(sharedWordChecks).isGreaterThan(1_000);
    }

    private static String outcome(Resolution resolution) {
        if (resolution instanceof Resolved resolved) {
            return resolved.shareClasses().size() == 1 ? "Resolved, one class" : "Resolved, several classes";
        }
        return resolution.getClass().getSimpleName();
    }

    /**
     * Parts of a fund, umbrella and class name, extra words, now and then an ISIN or Valor prefix (some too short to
     * count), shuffled, and now and then lowercase.
     */
    private static String mention(Random random, List<ShareClass> classes, List<String> mentionWords) {
        ShareClass shareClass = pick(random, classes);
        List<String> parts = new ArrayList<>();
        Stream.of(shareClass.fundName(), shareClass.umbrellaName().orElse(""), shareClass.shareClassName())
                .filter(name -> random.nextInt(3) > 0)
                .flatMap(name -> Arrays.stream(name.split(" ")))
                .filter(word -> !word.isBlank() && random.nextInt(4) > 0)
                .forEach(parts::add);
        for (int extra = random.nextInt(3); extra > 0; extra--) {
            parts.add(pick(random, mentionWords));
        }
        int identifier = random.nextInt(10);
        if (identifier == 0) {
            parts.add(shareClass.isin().value().substring(0, 3 + random.nextInt(10)));
        } else if (identifier == 1 && shareClass.valor().isPresent()) {
            parts.add(shareClass.valor().get().substring(0, 5 + random.nextInt(3)));
        }
        Collections.shuffle(parts, random);
        String mention = String.join(" ", parts);
        return random.nextInt(4) == 0 ? mention.toLowerCase(Locale.ROOT) : mention;
    }

    /**
     * The rule, written apart from the matcher. Within the fund, the typed words left once the fund and umbrella name
     * words are taken (counted) name a class when they hold its whole name and one of its name words some class of the
     * fund does not carry. Resolved is then every class of the fund when no word is left; one class its ISIN or Valor
     * prefix pins; or the one named class every other named class's name is part of, when no class whose name is typed
     * in full, fund words included, has its name or a longer one containing it.
     */
    private static void assertResolvedKeepsTheRule(String mention, Resolution resolution, List<ShareClass> all) {
        if (!(resolution instanceof Resolved resolved)) {
            return;
        }
        List<ShareClass> named = resolved.shareClasses();
        String fundKey = fundKey(named.getFirst());
        List<ShareClass> fundClasses = all.stream().filter(shareClass -> fundKey(shareClass).equals(fundKey)).toList();
        List<String> tokens = Arrays.asList(mention.split(" "));
        Map<String, Long> typed = wordCounts(tokens.stream()
                .filter(token -> !isIdentifierPrefix(token, fundClasses))
                .collect(Collectors.joining(" ")));
        boolean narrowed = tokens.stream().anyMatch(token -> isIdentifierPrefix(token, fundClasses)
                && !fundClasses.stream().allMatch(shareClass -> isIdentifierPrefix(token, List.of(shareClass))));
        String description = "'%s' over %s gave %s".formatted(mention, describe(all), describe(named));

        assertThat(named).as("never two funds: " + description)
                .allMatch(shareClass -> fundKey(shareClass).equals(fundKey));
        boolean fundAlone = named.equals(fundClasses) && !narrowed
                && fundClasses.stream().allMatch(shareClass -> containsAll(fundWords(shareClass), typed));
        if (named.size() > 1) {
            assertThat(fundAlone).as(description).isTrue();
            return;
        }
        ShareClass only = named.getFirst();
        List<ShareClass> others = fundClasses.stream().filter(other -> !other.equals(only)).toList();
        boolean pinned = tokens.stream()
                .anyMatch(token -> isIdentifierPrefix(token, List.of(only)) && !isIdentifierPrefix(token, others));
        if (!fundAlone) {
            assertThat(pinned || !Collections.disjoint(minus(typed, fundWords(only)).keySet(),
                    wordCounts(only.shareClassName()).keySet()))
                    .as("one class has a word of its name typed beyond the fund's, or its ISIN or Valor: " + description)
                    .isTrue();
        }
        boolean namedInFull = !narrowed && isNamed(only, typed, fundClasses)
                && others.stream().filter(other -> isNamed(other, typed, fundClasses))
                        .allMatch(other -> containsAll(classWords(only), classWords(other)))
                && others.stream().noneMatch(other -> containsAll(typed, classWords(other))
                        && containsAll(classWords(other), classWords(only)));
        assertThat(fundAlone || pinned || namedInFull).as(description).isTrue();
    }

    private static boolean isNamed(ShareClass shareClass, Map<String, Long> typed, List<ShareClass> fundClasses) {
        return containsAll(minus(typed, fundWords(shareClass)), classWords(shareClass))
                && (fundClasses.size() == 1 || classWords(shareClass).keySet().stream().anyMatch(word ->
                        fundClasses.stream().anyMatch(other -> !ownWords(other).contains(word))));
    }

    /**
     * When a mention gives every class of a fund, adding a word every class of it carries (a class name word, the
     * currency or the fund type) never gives one class. Returns how many words it tried.
     */
    private static int assertAWordEveryClassCarriesNeverPicksOne(String mention, Resolution resolution,
            List<ShareClass> all, ShareClassCatalog catalog) {
        if (!(resolution instanceof Resolved resolved) || resolved.shareClasses().size() < 2) {
            return 0;
        }
        Set<String> carried = new HashSet<>(ownWords(resolved.shareClasses().getFirst()));
        resolved.shareClasses().forEach(shareClass -> carried.retainAll(ownWords(shareClass)));
        for (String word : carried) {
            Resolution longer = catalog.resolve(mention + " " + word);
            assertThat(longer instanceof Resolved one && one.shareClasses().size() == 1)
                    .as("'%s' + '%s' over %s gave %s", mention, word, describe(all), longer)
                    .isFalse();
        }
        return carried.size();
    }

    private static String fundKey(ShareClass shareClass) {
        return shareClass.fundId().map(fundId -> "id:" + fundId).orElse("name:" + shareClass.fundName());
    }

    private static Map<String, Long> fundWords(ShareClass shareClass) {
        return wordCounts(shareClass.fundName() + " " + shareClass.umbrellaName().orElse(""));
    }

    private static Map<String, Long> classWords(ShareClass shareClass) {
        return wordCounts(shareClass.shareClassName());
    }

    /** The words a class carries beyond its fund's and umbrella's names. */
    private static Set<String> ownWords(ShareClass shareClass) {
        return wordCounts(String.join(" ", shareClass.shareClassName(), shareClass.currency(),
                shareClass.fundType().map(FundType::name).orElse(""))).keySet();
    }

    private static List<String> describe(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(shareClass -> "%s %s / %s / %s %s %s %s %s".formatted(
                shareClass.fundId().orElse("-"), shareClass.fundName(), shareClass.umbrellaName().orElse("-"),
                shareClass.shareClassName(), shareClass.currency(), shareClass.fundType().map(Enum::name).orElse("-"),
                shareClass.isin().value(), shareClass.valor().orElse("-"))).toList();
    }

    /** 3 characters with a digit, or 6 digits for a Valor, starting an ISIN or Valor of one of the classes. */
    private static boolean isIdentifierPrefix(String token, List<ShareClass> shareClasses) {
        String compact = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        int minLength = compact.chars().allMatch(Character::isDigit) ? 6 : 3;
        return compact.length() >= minLength && compact.chars().anyMatch(Character::isDigit)
                && shareClasses.stream().anyMatch(shareClass -> Stream.concat(
                        Stream.of(shareClass.isin().value()), shareClass.valor().stream())
                        .anyMatch(identifier -> identifier.toLowerCase(Locale.ROOT).startsWith(compact)));
    }

    private static Map<String, Long> wordCounts(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).replace('_', ' ').split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static Map<String, Long> minus(Map<String, Long> words, Map<String, Long> taken) {
        Map<String, Long> left = new HashMap<>(words);
        taken.forEach((word, count) -> left.computeIfPresent(word, (key, typed) -> typed > count ? typed - count : null));
        return left;
    }

    private static boolean containsAll(Map<String, Long> available, Map<String, Long> needed) {
        return needed.entrySet().stream().allMatch(word -> available.getOrDefault(word.getKey(), 0L) >= word.getValue());
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    @Test
    void a_resolution_never_names_an_empty_list() {
        assertThatThrownBy(() -> new Resolved(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Ambiguous(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CandidateFund("Fund", List.of())).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void a_catalog_source_that_gives_no_list_does_not_load() {
        assertThatThrownBy(() -> new ShareClassCatalog(() -> null)).isInstanceOf(FundDataUnavailable.class);
    }

    private List<String> resolvedIsins(String mention) {
        return isinsOf(resolved(catalog.resolve(mention)));
    }

    private static List<ShareClass> resolved(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Resolved.class);
        return ((Resolved) resolution).shareClasses();
    }

    private static List<List<String>> candidateIsins(Resolution resolution) {
        return ambiguous(resolution).funds().stream().map(fund -> isinsOf(fund.shareClasses())).toList();
    }

    private static Ambiguous ambiguous(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Ambiguous.class);
        return (Ambiguous) resolution;
    }

    private static ShareClass shareClass(String isin, Optional<String> fundId, String fundName, String className) {
        return new ShareClass(new Isin(isin), fundId, fundName, Optional.empty(), className, "CHF", Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH);
    }

    private static ShareClass inUmbrella(ShareClass shareClass, String umbrellaName) {
        return new ShareClass(shareClass.isin(), shareClass.fundId(), shareClass.fundName(), Optional.of(umbrellaName),
                shareClass.shareClassName(), shareClass.currency(), shareClass.domicile(), shareClass.fundType(),
                shareClass.productType(), shareClass.valor(), shareClass.internalCode(), shareClass.retrievedAt());
    }

    private static ShareClass inFundType(ShareClass shareClass, FundType fundType) {
        return new ShareClass(shareClass.isin(), shareClass.fundId(), shareClass.fundName(), shareClass.umbrellaName(),
                shareClass.shareClassName(), shareClass.currency(), shareClass.domicile(), Optional.of(fundType),
                shareClass.productType(), shareClass.valor(), shareClass.internalCode(), shareClass.retrievedAt());
    }

    private static ShareClass inCurrency(ShareClass shareClass, String currency) {
        return new ShareClass(shareClass.isin(), shareClass.fundId(), shareClass.fundName(), shareClass.umbrellaName(),
                shareClass.shareClassName(), currency, shareClass.domicile(), shareClass.fundType(),
                shareClass.productType(), shareClass.valor(), shareClass.internalCode(), shareClass.retrievedAt());
    }

    private static ShareClass withValor(ShareClass shareClass, String valor) {
        return new ShareClass(shareClass.isin(), shareClass.fundId(), shareClass.fundName(), shareClass.umbrellaName(),
                shareClass.shareClassName(), shareClass.currency(), shareClass.domicile(), shareClass.fundType(),
                shareClass.productType(), Optional.of(valor), shareClass.internalCode(), shareClass.retrievedAt());
    }

    private static List<String> isinsOf(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(shareClass -> shareClass.isin().value()).toList();
    }
}
