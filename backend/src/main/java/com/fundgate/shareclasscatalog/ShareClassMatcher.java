package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
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

    /** A Share Class with the words the matcher reads, split once (the catalog keeps these for every mention). */
    record IndexedClass(ShareClass shareClass, Set<String> textWords, Map<String, Long> fundNameWords,
            Map<String, Long> fundWords, Map<String, Long> classWords, List<String> identifiers) {
    }

    /**
     * The matches of one fund, under the name of its first class in catalog order. {@code named}: the classes are the
     * ones the text names, by the rule of {@link Resolution.Resolved}; otherwise they are the candidates the dealer
     * chooses from. {@code fundNamedInFull}: the text gives every word of the fund's name.
     */
    record FundMatch(String fundName, List<ShareClass> shareClasses, boolean named, boolean fundNamedInFull) {
    }

    static List<IndexedClass> index(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(ShareClassMatcher::indexed).toList();
    }

    /** {@link #match}, fund by fund, in catalog order, each fund with what the text names of it. */
    static List<FundMatch> matchByFund(String text, List<IndexedClass> catalog) {
        List<Typed> typed = typed(text);
        if (typed.isEmpty()) {
            return List.of();
        }
        Map<String, List<IndexedClass>> byFund = catalog.stream().collect(Collectors.groupingBy(
                shareClass -> fundKey(shareClass.shareClass()), LinkedHashMap::new, Collectors.toList()));
        return byFund.values().stream()
                .map(fundClasses -> fundMatch(typed, fundClasses))
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
     * What the text names of one fund, by the rule of {@link Resolution.Resolved}: the one class an ISIN or Valor
     * prefix leaves (a prefix that other classes of the fund share, narrowed by a word, is in doubt),
     * every class when each typed word is the fund's or the umbrella's, or the one class whose whole name is typed and
     * that no longer or equal typed class name of the fund contains, whatever else matched (two classes I Cap told apart
     * only by currency are the dealer's to pick). A class name typed beyond the fund's own words outranks one that needs
     * them ("Bond Fund I P Cap" is class P Cap, not class I), unless a longer class name needs them ("Bonds CHF Hedged"
     * may be class Hedged or class CHF Hedged). Anything else gives the matched classes whose whole name is typed, or
     * failing those every matched class, as candidates.
     */
    private static Optional<FundMatch> fundMatch(List<Typed> typed, List<IndexedClass> fundClasses) {
        List<IndexedClass> matched = fundClasses.stream().filter(shareClass -> matchesAll(typed, shareClass)).toList();
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        String fundName = fundClasses.getFirst().shareClass().fundName();
        Map<String, Long> words = counts(typed.stream()
                .filter(word -> fundClasses.stream().noneMatch(word::isIdentifierPrefixOf))
                .flatMap(word -> word.words().stream()));
        boolean fundNamedInFull = matched.stream().anyMatch(shareClass -> containsAll(words, shareClass.fundNameWords()));
        Function<List<IndexedClass>, FundMatch> named = classes ->
                new FundMatch(fundName, shareClassesOf(classes), true, fundNamedInFull);
        Function<List<IndexedClass>, FundMatch> candidates = classes ->
                new FundMatch(fundName, shareClassesOf(classes), false, fundNamedInFull);

        List<List<IndexedClass>> prefixed = typed.stream()
                .map(word -> fundClasses.stream().filter(word::isIdentifierPrefixOf).toList())
                .filter(classes -> !classes.isEmpty() && classes.size() < fundClasses.size())
                .toList();
        if (!prefixed.isEmpty()) {
            return Optional.of(prefixed.contains(matched) && matched.size() == 1
                    ? named.apply(matched) : candidates.apply(matched));
        }
        if (matched.equals(fundClasses)
                && fundClasses.stream().allMatch(shareClass -> containsAll(shareClass.fundWords(), words))) {
            return Optional.of(named.apply(fundClasses));
        }
        List<IndexedClass> typedInFull = fundClasses.stream()
                .filter(shareClass -> containsAll(words, shareClass.classWords()))
                .toList();
        List<IndexedClass> typedBeyondFund = typedInFull.stream()
                .filter(shareClass -> containsAll(minus(words, shareClass.fundWords()), shareClass.classWords()))
                .toList();
        Optional<IndexedClass> pick = longest(typedBeyondFund.isEmpty() ? typedInFull : typedBeyondFund)
                .filter(shareClass -> matched.contains(shareClass)
                        && typedInFull.stream().noneMatch(other -> other != shareClass
                                && containsAll(other.classWords(), shareClass.classWords())));
        if (pick.isPresent()) {
            return Optional.of(named.apply(List.of(pick.get())));
        }
        List<IndexedClass> matchedInFull = matched.stream().filter(typedInFull::contains).toList();
        return Optional.of(candidates.apply(matchedInFull.isEmpty() ? matched : matchedInFull));
    }

    /** The class whose name contains every other's and is longer: class A AC of A and A AC; none of I and P Cap. */
    private static Optional<IndexedClass> longest(List<IndexedClass> shareClasses) {
        return shareClasses.stream()
                .filter(shareClass -> shareClasses.stream()
                        .allMatch(other -> other == shareClass || hasLongerName(shareClass, other)))
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
        Set<String> textWords = Set.copyOf(words(String.join(" ", shareClass.fundName(),
                shareClass.umbrellaName().orElse(""), shareClass.shareClassName(), shareClass.currency(), fundType,
                shareClass.internalCode().orElse(""))));
        List<String> identifiers = Stream.of(Optional.of(shareClass.isin().value()), shareClass.valor())
                .flatMap(Optional::stream)
                .map(ShareClassMatcher::compact)
                .toList();
        return new IndexedClass(shareClass, textWords, counts(words(shareClass.fundName()).stream()),
                counts(words(shareClass.fundName() + " " + shareClass.umbrellaName().orElse("")).stream()),
                counts(words(shareClass.shareClassName()).stream()), identifiers);
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
