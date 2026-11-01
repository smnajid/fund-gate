package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.text.Normalizer;
import java.util.ArrayList;
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
     * Every Share Class, in the order given, that the text names; a null or blank text names none. Within one fund,
     * when the text names some classes in full (every word of the class name), only those are kept, and of those only
     * the most specific: "BNP Bonds CHF A" gives class A, not also class A AC. A fund named without a class gives all
     * its classes. Classes of several funds may come back: the search lists them all, and the catalog flags them as
     * Ambiguous.
     */
    public static List<ShareClass> match(String text, List<ShareClass> shareClasses) {
        Set<ShareClass> kept = matchByFund(text, index(shareClasses)).stream()
                .flatMap(fund -> fund.shareClasses().stream())
                .collect(Collectors.toSet());
        return shareClasses.stream().filter(kept::contains).toList();
    }

    /** A Share Class with the words the matcher reads, split once (the catalog keeps these for every mention). */
    record IndexedClass(ShareClass shareClass, Set<String> textWords, Map<String, Long> fundWords,
            Map<String, Long> classWords, List<String> identifiers) {
    }

    /**
     * The matches of one fund, in catalog order. {@code classInDoubt}: a word the fund name accounts for may instead
     * complete a longer class name, so the matches are the candidate classes, not a confident pick.
     * {@code fundNamedInFull}: the text gives every word of the fund's name.
     */
    record FundMatch(List<ShareClass> shareClasses, boolean classInDoubt, boolean fundNamedInFull) {
    }

    static List<IndexedClass> index(List<ShareClass> shareClasses) {
        return shareClasses.stream().map(ShareClassMatcher::indexed).toList();
    }

    /** {@link #match}, fund by fund, in the order each fund first appears. */
    static List<FundMatch> matchByFund(String text, List<IndexedClass> catalog) {
        if (text == null) {
            return List.of();
        }
        List<Typed> typed = Arrays.stream(WHITESPACE.split(text.strip()))
                .map(Typed::of)
                .filter(word -> !word.compact().isEmpty())
                .toList();
        if (typed.isEmpty()) {
            return List.of();
        }
        Map<String, List<IndexedClass>> byFund = new LinkedHashMap<>();
        for (IndexedClass shareClass : catalog) {
            if (typed.stream().allMatch(word -> word.matches(shareClass))) {
                byFund.computeIfAbsent(fundKey(shareClass.shareClass()), key -> new ArrayList<>()).add(shareClass);
            }
        }
        Map<String, Long> allTyped = counts(typed.stream().flatMap(word -> word.words().stream()));
        return byFund.values().stream().map(fundMatches -> fundMatch(typed, allTyped, fundMatches)).toList();
    }

    /**
     * The fund a class belongs to: FMD's fund id when it has one (contracts/share-classes.md); without it, the fund
     * name stands in for the Rule Card's ISIN list the contract joins on (hypotheses.md, FMD fields).
     */
    static String fundKey(ShareClass shareClass) {
        return shareClass.fundId().map(fundId -> "id:" + fundId).orElse("name:" + shareClass.fundName());
    }

    /**
     * The classes of one fund the typed words name in full, keeping the most specific, or all of them when none is
     * named. When a fund-name word may instead complete a longer class name ("Bonds CHF Hedged": class Hedged, or
     * class CHF Hedged of a fund shortened to "Bonds"), the longer classes join the candidates, in doubt.
     */
    private static FundMatch fundMatch(List<Typed> typed, Map<String, Long> allTyped, List<IndexedClass> fundMatches) {
        boolean fundNamedInFull = fundMatches.stream()
                .anyMatch(shareClass -> containsAll(allTyped, shareClass.fundWords()));
        List<IndexedClass> named = mostSpecific(fundMatches.stream()
                .filter(shareClass -> containsAll(classWords(typed, shareClass), shareClass.classWords()))
                .toList());
        if (named.isEmpty()) {
            return new FundMatch(shareClassesOf(fundMatches), false, fundNamedInFull);
        }
        List<IndexedClass> longer = fundMatches.stream()
                .filter(shareClass -> containsAll(typedWords(typed, shareClass), shareClass.classWords())
                        && named.stream().anyMatch(namedClass -> hasLongerName(shareClass, namedClass)))
                .toList();
        List<IndexedClass> candidates = fundMatches.stream()
                .filter(shareClass -> named.contains(shareClass) || longer.contains(shareClass))
                .toList();
        return new FundMatch(shareClassesOf(candidates), !longer.isEmpty(), fundNamedInFull);
    }

    /** The named classes no other named class's name contains: class A AC over class A. */
    private static List<IndexedClass> mostSpecific(List<IndexedClass> named) {
        return named.stream()
                .filter(shareClass -> named.stream().noneMatch(other -> hasLongerName(other, shareClass)))
                .toList();
    }

    private static boolean hasLongerName(IndexedClass longer, IndexedClass shorter) {
        Set<String> longerName = longer.classWords().keySet();
        Set<String> shorterName = shorter.classWords().keySet();
        return longerName.size() > shorterName.size() && longerName.containsAll(shorterName);
    }

    /**
     * The typed words left once the fund and the umbrella have taken theirs, counted: a typed word names the class
     * only as often as it is typed beyond the fund name's own uses of it. So the "I" of "Bond Fund I" names the fund,
     * not its class I, while the second "CHF" of "Bonds CHF A CHF" names class A CHF.
     */
    private static Map<String, Long> classWords(List<Typed> typed, IndexedClass shareClass) {
        Map<String, Long> left = new HashMap<>(typedWords(typed, shareClass));
        shareClass.fundWords().forEach((word, count) -> left.computeIfPresent(word, (key, typedCount) ->
                typedCount > count ? typedCount - count : null));
        return left;
    }

    /** The typed words, counted, once the identifiers have taken theirs. */
    private static Map<String, Long> typedWords(List<Typed> typed, IndexedClass shareClass) {
        return counts(typed.stream()
                .filter(word -> !word.isIdentifierPrefixOf(shareClass))
                .flatMap(word -> word.words().stream()));
    }

    /** Every word of {@code needed}, as many times as it occurs there. */
    private static boolean containsAll(Map<String, Long> available, Map<String, Long> needed) {
        return needed.entrySet().stream()
                .allMatch(word -> available.getOrDefault(word.getKey(), 0L) >= word.getValue());
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
        return new IndexedClass(shareClass, textWords,
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
