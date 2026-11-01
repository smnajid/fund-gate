package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one matcher for an email's fund mention and the reverse-lookup search (screens.md, Search). Case, accents and
 * punctuation are ignored; every typed word must appear, in any order; an ISIN or Valor matches exactly or by prefix.
 * No fuzzy guessing, so the same text always gives the same Share Classes.
 */
public final class ShareClassMatcher {

    /**
     * "LU" or "XXSL" would match whole countries or every placeholder of ISINs, so a prefix shorter than this, or with
     * no digit, is read as a word only.
     */
    private static final int MIN_IDENTIFIER_PREFIX = 3;
    /** Valors are 6 to 9 digits: a shorter number, like the "123" of a fund name, is a word, not a Valor prefix. */
    private static final int MIN_VALOR_PREFIX = 6;
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern ALL_DIGITS = Pattern.compile("\\d+");

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ShareClassMatcher() {
    }

    /**
     * Every Share Class, in the order given, that the text matches; a null or blank text matches none. Classes of
     * several funds, and several classes of one fund, may come back: the search lists them all, and the catalog decides
     * which of them a mention names ({@link #matchByFund}).
     */
    public static List<ShareClass> match(String text, List<ShareClass> shareClasses) {
        List<Typed> typed = typed(text);
        if (typed.isEmpty()) {
            return List.of();
        }
        return index(shareClasses).stream()
                .filter(shareClass -> matchesAll(typed, shareClass))
                .map(IndexedClass::shareClass)
                .toList();
    }

    /**
     * A Share Class with the words the matcher reads, split once (the catalog keeps these for every mention): the
     * words of its fund and umbrella names, counted (its fund's are all its classes' together, {@link IndexedFund}), of its class name, counted, and the words it carries beyond its
     * fund's: class name, currency, fund type and the bank's own code.
     */
    record IndexedClass(ShareClass shareClass, Set<String> textWords, Map<String, Long> fundWords,
            Map<String, Long> classWords, Set<String> ownWords, List<String> identifiers) {
    }

    /**
     * The classes of one fund, under the name of its first class in catalog order; the words of the fund and umbrella
     * names of all of them, each counted at the most any one class uses it (a fund id may group classes under different
     * fund names, a fund name classes under different umbrellas, and a word of any of them is the fund's); and the
     * words every class carries beyond those (none for a fund of one class): such a word tells no class from another.
     */
    record IndexedFund(String fundName, List<IndexedClass> shareClasses, Map<String, Long> fundWords,
            Set<String> wordsEveryClassCarries) {
    }

    /**
     * The matches of one fund. {@code named}: the classes are the ones the text names, by the rule of
     * {@link Resolution.Resolved}; otherwise they are the candidates the dealer chooses from.
     */
    record FundMatch(String fundName, List<ShareClass> shareClasses, boolean named) {
    }

    static List<IndexedClass> index(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(ShareClassMatcher::indexed).toList();
    }

    /** The catalog's classes, indexed and grouped by fund, in catalog order. */
    static List<IndexedFund> indexByFund(List<ShareClass> shareClasses) {
        Map<String, List<IndexedClass>> byFund = index(shareClasses).stream().collect(Collectors.groupingBy(
                shareClass -> fundKey(shareClass.shareClass()), LinkedHashMap::new, Collectors.toList()));
        return byFund.values().stream().map(ShareClassMatcher::indexedFund).toList();
    }

    private static IndexedFund indexedFund(List<IndexedClass> shareClasses) {
        Set<String> carried = new HashSet<>(shareClasses.getFirst().ownWords());
        shareClasses.forEach(shareClass -> carried.retainAll(shareClass.ownWords()));
        Map<String, Long> fundWords = new HashMap<>();
        shareClasses.forEach(shareClass -> shareClass.fundWords().forEach((word, count) ->
                fundWords.merge(word, count, Math::max)));
        return new IndexedFund(shareClasses.getFirst().shareClass().fundName(), shareClasses, Map.copyOf(fundWords),
                shareClasses.size() == 1 ? Set.of() : Set.copyOf(carried));
    }

    /** {@link #match}, fund by fund, in catalog order, each fund with what the text names of it. */
    static List<FundMatch> matchByFund(String text, List<IndexedFund> catalog) {
        List<Typed> typed = typed(text);
        if (typed.isEmpty()) {
            return List.of();
        }
        return catalog.stream()
                .map(fund -> fundMatch(typed, fund))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The fund a class belongs to: FMD's fund id when it has one (contracts/share-classes.md); without it, the fund
     * name stands in for the Rule Card's ISIN list the contract joins on (hypotheses.md, FMD fields).
     */
    static String fundKey(ShareClass shareClass) {
        return shareClass.fundId().map(fundId -> "id:" + fundId).orElse("name:" + shareClass.fundName());
    }

    /**
     * What the text names of one fund, by the rule of {@link Resolution.Resolved}. The typed words, ISIN and Valor
     * prefixes aside, less the words of the fund and umbrella names of every class of the fund (counted: a word typed
     * more often than any one class's names use it keeps the extra), name a class when they hold its whole class name
     * and one of its words some class of the fund does not carry. A word of a sibling class's fund or umbrella name is
     * the fund's too: with fund id F1 over "Bond Fund" class I and "Bond Fund I" class R, "Bond Fund I" is the fund,
     * not class I. Then:
     * <ul>
     * <li>an ISIN or Valor prefix some classes of the fund share and others not: the one class it leaves, or else the
     * classes it leaves as candidates;</li>
     * <li>no word left beyond the fund and umbrella names, and every class of the fund matched: every class of the
     * fund (the fan-out);</li>
     * <li>one named class every other named class's name is part of, and no class whose name is typed in full, fund
     * words included, with that name or a longer one containing it: that class;</li>
     * <li>anything else: the matched classes named, or failing those every matched class, as candidates.</li>
     * </ul>
     */
    private static Optional<FundMatch> fundMatch(List<Typed> typed, IndexedFund fund) {
        List<IndexedClass> fundClasses = fund.shareClasses();
        List<IndexedClass> matched = fundClasses.stream().filter(shareClass -> matchesAll(typed, shareClass)).toList();
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        Map<Typed, List<IndexedClass>> prefixed = typed.stream().distinct().collect(Collectors.toMap(
                Function.identity(), word -> fundClasses.stream().filter(word::isIdentifierPrefixOf).toList()));
        boolean narrowedByPrefix = prefixed.values().stream()
                .anyMatch(classes -> !classes.isEmpty() && classes.size() < fundClasses.size());
        if (narrowedByPrefix) {
            boolean pinned = matched.size() == 1 && prefixed.containsValue(matched);
            return Optional.of(fundMatch(fund, matched, pinned));
        }
        Map<String, Long> words = counts(typed.stream()
                .filter(word -> prefixed.get(word).isEmpty())
                .flatMap(word -> word.words().stream()));
        Map<String, Long> beyondFund = minus(words, fund.fundWords());
        if (beyondFund.isEmpty() && matched.size() == fundClasses.size()) {
            return Optional.of(fundMatch(fund, fundClasses, true));
        }
        List<IndexedClass> named = fundClasses.stream()
                .filter(shareClass -> isNamed(shareClass, beyondFund, fund.wordsEveryClassCarries()))
                .toList();
        Optional<IndexedClass> pick = mostSpecific(named)
                .filter(matched::contains)
                .filter(shareClass -> !aLongerNameIsTypedInFull(shareClass, fundClasses, words));
        if (pick.isPresent()) {
            return Optional.of(fundMatch(fund, List.of(pick.get()), true));
        }
        List<IndexedClass> matchedAndNamed = matched.stream().filter(named::contains).toList();
        return Optional.of(fundMatch(fund, matchedAndNamed.isEmpty() ? matched : matchedAndNamed, false));
    }

    private static FundMatch fundMatch(IndexedFund fund, List<IndexedClass> shareClasses, boolean named) {
        return new FundMatch(fund.fundName(), shareClassesOf(shareClasses), named);
    }

    /**
     * Its whole class name in the words the fund and umbrella names of its fund leave, with a word that tells it from
     * another.
     */
    private static boolean isNamed(IndexedClass shareClass, Map<String, Long> beyondFund,
            Set<String> everyClassCarries) {
        return containsAll(beyondFund, shareClass.classWords())
                && !everyClassCarries.containsAll(shareClass.classWords().keySet());
    }

    /**
     * Another class whose name contains this one's is typed in full, fund words included: "Bonds CHF Hedged" may be
     * class Hedged of fund Bonds CHF, or class CHF Hedged under a shortened fund name.
     */
    private static boolean aLongerNameIsTypedInFull(IndexedClass shareClass, List<IndexedClass> fundClasses,
            Map<String, Long> words) {
        return fundClasses.stream().anyMatch(other -> !other.equals(shareClass)
                && containsAll(words, other.classWords()) && containsAll(other.classWords(), shareClass.classWords()));
    }

    /** The class whose name contains every other's and is longer: class A AC of A and A AC; none of I and P Cap. */
    private static Optional<IndexedClass> mostSpecific(List<IndexedClass> shareClasses) {
        return shareClasses.stream()
                .filter(shareClass -> shareClasses.stream()
                        .allMatch(other -> other.equals(shareClass) || hasLongerName(shareClass, other)))
                .findFirst();
    }

    private static boolean hasLongerName(IndexedClass longer, IndexedClass shorter) {
        return containsAll(longer.classWords(), shorter.classWords())
                && size(longer.classWords()) > size(shorter.classWords());
    }

    private static boolean matchesAll(List<Typed> typed, IndexedClass shareClass) {
        return typed.stream().allMatch(word -> word.matches(shareClass));
    }

    /** Every word of {@code needed}, as many times as it occurs there. */
    private static boolean containsAll(Map<String, Long> available, Map<String, Long> needed) {
        return needed.entrySet().stream()
                .allMatch(word -> available.getOrDefault(word.getKey(), 0L) >= word.getValue());
    }

    /** The words left once {@code taken} has taken its own, counted. */
    private static Map<String, Long> minus(Map<String, Long> words, Map<String, Long> taken) {
        Map<String, Long> left = new HashMap<>(words);
        taken.forEach((word, count) -> left.computeIfPresent(word, (key, typedCount) ->
                typedCount > count ? typedCount - count : null));
        return left;
    }

    private static long size(Map<String, Long> words) {
        return words.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * The fields searched word by word: fund, umbrella, class, currency, fund regime and the bank's own code
     * (contracts/share-classes.md, "Read by: matcher"; screens.md, Search); and the fields matched exactly or by
     * prefix.
     */
    private static IndexedClass indexed(ShareClass shareClass) {
        String fundType = shareClass.fundType().map(type -> type.name().replace('_', ' ')).orElse("");
        List<String> ownWords = words(String.join(" ", shareClass.shareClassName(), shareClass.currency(), fundType,
                shareClass.internalCode().orElse("")));
        List<String> fundWords = words(shareClass.fundName() + " " + shareClass.umbrellaName().orElse(""));
        Set<String> textWords = Stream.concat(fundWords.stream(), ownWords.stream()).collect(Collectors.toSet());
        List<String> identifiers = Stream.of(Optional.of(shareClass.isin().value()), shareClass.valor())
                .flatMap(Optional::stream)
                .map(ShareClassMatcher::compact)
                .toList();
        return new IndexedClass(shareClass, Set.copyOf(textWords), counts(fundWords.stream()),
                counts(words(shareClass.shareClassName()).stream()), Set.copyOf(ownWords), identifiers);
    }

    /** One typed word, which may hold several words once punctuation is dropped ("BI-EUR"). */
    private record Typed(String compact, List<String> words) {

        static Typed of(String typed) {
            return new Typed(ShareClassMatcher.compact(typed), ShareClassMatcher.words(typed));
        }

        boolean matches(IndexedClass shareClass) {
            return isIdentifierPrefixOf(shareClass) || shareClass.textWords().containsAll(words);
        }

        boolean isIdentifierPrefixOf(IndexedClass shareClass) {
            int minLength = ALL_DIGITS.matcher(compact).matches() ? MIN_VALOR_PREFIX : MIN_IDENTIFIER_PREFIX;
            return compact.length() >= minLength && DIGIT.matcher(compact).find()
                    && shareClass.identifiers().stream().anyMatch(identifier -> identifier.startsWith(compact));
        }
    }

    private static List<Typed> typed(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(WHITESPACE.split(text.strip()))
                .map(Typed::of)
                .filter(word -> !word.compact().isEmpty())
                .toList();
    }

    private static List<ShareClass> shareClassesOf(List<IndexedClass> shareClasses) {
        return shareClasses.stream().map(IndexedClass::shareClass).toList();
    }

    private static Map<String, Long> counts(Stream<String> words) {
        return words.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static List<String> words(String text) {
        return Arrays.stream(NOT_ALPHANUMERIC.split(normalise(text)))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String compact(String text) {
        return NOT_ALPHANUMERIC.matcher(normalise(text)).replaceAll("");
    }

    private static String normalise(String text) {
        String withoutAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }
}
