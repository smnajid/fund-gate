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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
     * The whole class name typed picks the class even when a fund word also gives one of its words: the "1" of
     * "Nordea 1" never turns class I-A 1 into class I-A 2.
     */
    @Test
    void a_class_named_in_full_with_a_word_the_fund_name_shares_is_that_class() {
        ShareClassCatalog fundX = new ShareClassCatalog(() -> List.of(
                inUmbrella(shareClass("XXFX00000001", Optional.empty(), "Nordea 1 - Fund X", "I-A 1"), "Nordea 1, SICAV"),
                inUmbrella(shareClass("XXFX00000002", Optional.empty(), "Nordea 1 - Fund X", "I-A 2"), "Nordea 1, SICAV")));
        ShareClassCatalog bondFund = new ShareClassCatalog(() -> List.of(
                shareClass("XXBFI0000001", Optional.empty(), "Bond Fund I", "I Cap"),
                shareClass("XXBFP0000001", Optional.empty(), "Bond Fund I", "P Cap")));
        ShareClassCatalog bondsChf = new ShareClassCatalog(() -> List.of(
                shareClass("XXBC00000003", Optional.empty(), "Bonds CHF", "CHF Hedged"),
                shareClass("XXBC00000004", Optional.empty(), "Bonds CHF", "EUR Hedged")));

        assertThat(isinsOf(resolved(fundX.resolve("Fund X I-A 1")))).containsExactly("XXFX00000001");
        assertThat(isinsOf(resolved(fundX.resolve("Nordea 1 Fund X I-A 1")))).containsExactly("XXFX00000001");
        assertThat(isinsOf(resolved(bondFund.resolve("Bond Fund I Cap")))).containsExactly("XXBFI0000001");
        assertThat(isinsOf(resolved(bondsChf.resolve("Bonds CHF Hedged")))).containsExactly("XXBC00000003");
    }

    /** "(USD)" names the fund here, so class (USD) P-acc is picked, never both P-acc classes as if the fund were named. */
    @Test
    void a_class_word_beyond_the_fund_name_never_fans_out_to_every_class() {
        ShareClassCatalog usdCorporates = new ShareClassCatalog(() -> List.of(
                shareClass("XXUBS0000001", Optional.empty(), "UBS Bond - USD Corporates (USD)", "(USD) P-acc"),
                shareClass("XXUBS0000002", Optional.empty(), "UBS Bond - USD Corporates (USD)", "(EUR hedged) P-acc")));

        assertThat(isinsOf(resolved(usdCorporates.resolve("UBS Bond USD Corporates (USD) P-acc"))))
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

    /** Of several matching funds, the one whose whole name the mention gives is the one it means. */
    @Test
    void a_mention_that_gives_one_fund_name_in_full_resolves_to_that_fund() {
        ShareClassCatalog globalBonds = new ShareClassCatalog(() -> List.of(
                shareClass("XXGB00000001", Optional.empty(), "Global Bond", "A"),
                shareClass("XXGB00000002", Optional.empty(), "Global Bond", "B"),
                shareClass("XXGBP0000001", Optional.empty(), "Global Bond Plus", "A")));

        assertThat(isinsOf(resolved(globalBonds.resolve("Global Bond A")))).containsExactly("XXGB00000001");
        assertThat(resolvedIsins("Placeholder fund without a Rule Card A")).containsExactly(NOT_COVERED_A);
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
     * The invariant of {@link Resolution.Resolved}, over small catalogs whose fund, umbrella and class names share
     * words, and mentions mixing those words (fixed seed, so the same cases every run): several classes only when
     * every typed word is the fund's or the umbrella's, one class only when its whole name or its ISIN is typed and no
     * longer or equal class name is, and never classes of two funds.
     */
    @Test
    void a_resolved_mention_names_the_fund_alone_or_one_class_in_full() {
        List<String> fundNames = List.of("Bond Fund I", "Bonds CHF", "Fund A", "Bond Fund", "Fund I-A 1", "CHF Bond",
                "Global Bond USD", "Bond 2 Fund");
        List<String> umbrellaNames = List.of("", "", "Fund 1", "Bond I", "USD Cap");
        List<String> classNames = List.of("I", "A", "I Cap", "P Cap", "CHF Hedged", "EUR Hedged", "Hedged", "P-acc",
                "(USD) P-acc", "I-A 1", "I-A 2", "A AC", "1", "A CHF", "EUR", "Cap");
        List<String> currencies = List.of("CHF", "EUR", "USD");
        List<String> mentionWords = List.of("I", "A", "CHF", "EUR", "USD", "Cap", "Hedged", "P-acc", "1", "2", "Bond",
                "Bonds", "Fund", "AC", "Global", "I-A", "P");
        Random random = new Random(60);
        int cases = 0;
        for (int catalogNo = 0; catalogNo < 1500; catalogNo++) {
            List<ShareClass> classes = new ArrayList<>();
            int funds = 1 + random.nextInt(2);
            for (int fund = 0; fund < funds; fund++) {
                String fundName = pick(random, fundNames);
                String umbrella = pick(random, umbrellaNames);
                int classCount = 1 + random.nextInt(3);
                for (int classNo = 0; classNo < classCount; classNo++) {
                    ShareClass shareClass = inCurrency(shareClass("XX%02d%08d".formatted(fund, catalogNo * 10 + classNo),
                            Optional.empty(), fundName, pick(random, classNames)), pick(random, currencies));
                    classes.add(umbrella.isEmpty() ? shareClass : inUmbrella(shareClass, umbrella));
                }
            }
            ShareClassCatalog generated = new ShareClassCatalog(() -> classes);
            for (int mentionNo = 0; mentionNo < 30; mentionNo++) {
                String mention = mention(random, classes, mentionWords);
                assertResolvedKeepsTheInvariant(mention, generated.resolve(mention), classes);
                cases++;
            }
        }
        assertThat(cases).isEqualTo(45_000);
    }

    /** Parts of a fund, umbrella and class name, extra words, and now and then an ISIN prefix, shuffled. */
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
        if (random.nextInt(10) == 0) {
            parts.add(shareClass.isin().value().substring(0, 4 + random.nextInt(9)));
        }
        Collections.shuffle(parts, random);
        return String.join(" ", parts);
    }

    private static void assertResolvedKeepsTheInvariant(String mention, Resolution resolution, List<ShareClass> all) {
        if (!(resolution instanceof Resolved resolved)) {
            return;
        }
        List<ShareClass> named = resolved.shareClasses();
        String fundName = named.getFirst().fundName();
        List<ShareClass> fundClasses = all.stream().filter(shareClass -> shareClass.fundName().equals(fundName)).toList();
        List<String> typed = Arrays.stream(mention.split(" ")).filter(word -> !isIsinPrefix(word, fundClasses)).toList();
        Map<String, Long> typedWords = wordCounts(String.join(" ", typed));
        String description = "'%s' over %s gave %s".formatted(mention, describe(all), describe(named));

        assertThat(named).as(description).allMatch(shareClass -> shareClass.fundName().equals(fundName));
        boolean fundAlone = named.equals(fundClasses) && fundClasses.stream().allMatch(shareClass -> containsAll(
                wordCounts(fundName + " " + shareClass.umbrellaName().orElse("")), typedWords));
        if (named.size() > 1) {
            assertThat(fundAlone).as(description).isTrue();
            return;
        }
        ShareClass only = named.getFirst();
        boolean pinned = Arrays.stream(mention.split(" "))
                .anyMatch(word -> isIsinPrefix(word, List.of(only)) && !isIsinPrefix(word, fundClasses.stream()
                        .filter(other -> other != only).toList()));
        boolean namedInFull = containsAll(typedWords, wordCounts(only.shareClassName()))
                && fundClasses.stream().filter(other -> other != only).noneMatch(other ->
                        containsAll(typedWords, wordCounts(other.shareClassName()))
                                && containsAll(wordCounts(other.shareClassName()), wordCounts(only.shareClassName())));
        assertThat(fundAlone || pinned || namedInFull).as(description).isTrue();
    }

    private static List<String> describe(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(shareClass -> "%s / %s / %s %s %s".formatted(shareClass.fundName(),
                shareClass.umbrellaName().orElse("-"), shareClass.shareClassName(), shareClass.currency(),
                shareClass.isin().value())).toList();
    }

    private static boolean isIsinPrefix(String word, List<ShareClass> shareClasses) {
        return word.length() >= 3 && word.chars().anyMatch(Character::isDigit)
                && shareClasses.stream().anyMatch(shareClass -> shareClass.isin().value().startsWith(word));
    }

    private static Map<String, Long> wordCounts(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
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
