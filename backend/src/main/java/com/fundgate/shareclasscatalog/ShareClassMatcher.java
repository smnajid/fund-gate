package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final Pattern DIGIT = Pattern.compile("\\d");

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
        if (text == null) {
            return List.of();
        }
        List<String> typedWords = Arrays.stream(WHITESPACE.split(text.strip()))
                .filter(typed -> !compact(typed).isEmpty())
                .toList();
        if (typedWords.isEmpty()) {
            return List.of();
        }
        List<ShareClass> matches = shareClasses.stream()
                .filter(shareClass -> typedWords.stream().allMatch(typed -> wordMatches(typed, shareClass)))
                .toList();
        Map<String, List<ShareClass>> byFund = matches.stream()
                .collect(Collectors.groupingBy(ShareClassMatcher::fundKey));
        Set<ShareClass> kept = byFund.values().stream()
                .flatMap(fundMatches -> namedInFull(typedWords, fundMatches).stream())
                .collect(Collectors.toSet());
        return matches.stream().filter(kept::contains).toList();
    }

    /**
     * The fund a class belongs to: FMD's fund id when it has one (contracts/share-classes.md); without it, the fund
     * name stands in for the Rule Card's ISIN list the contract joins on (hypotheses.md, FMD fields).
     */
    static String fundKey(ShareClass shareClass) {
        return shareClass.fundId().map(fundId -> "id:" + fundId).orElse("name:" + shareClass.fundName());
    }

    private static boolean wordMatches(String typed, ShareClass shareClass) {
        return isIdentifierPrefix(typed, shareClass) || textWords(shareClass).containsAll(words(typed));
    }

    private static boolean isIdentifierPrefix(String typed, ShareClass shareClass) {
        String prefix = compact(typed);
        return prefix.length() >= MIN_IDENTIFIER_PREFIX && DIGIT.matcher(prefix).find()
                && identifiers(shareClass).anyMatch(identifier -> identifier.startsWith(prefix));
    }

    /** The matches of one fund that the typed words name in full, or all of them when none is named. */
    private static List<ShareClass> namedInFull(List<String> typedWords, List<ShareClass> fundMatches) {
        List<Set<String>> fullyNamed = fundMatches.stream()
                .map(shareClass -> words(shareClass.shareClassName()))
                .distinct()
                .filter(className -> fundMatches.stream().anyMatch(
                        shareClass -> words(shareClass.shareClassName()).equals(className)
                                && classWords(typedWords, shareClass).containsAll(className)))
                .toList();
        if (fullyNamed.isEmpty()) {
            return fundMatches;
        }
        return fundMatches.stream()
                .filter(shareClass -> {
                    Set<String> className = words(shareClass.shareClassName());
                    return fullyNamed.contains(className) && fullyNamed.stream().noneMatch(
                            other -> other.size() > className.size() && other.containsAll(className));
                })
                .toList();
    }

    /**
     * The typed words left once the fund, the umbrella and the identifiers have taken theirs: only these may name a
     * class, so the "I" of "Bond Fund I" names the fund, not its class I.
     */
    private static Set<String> classWords(List<String> typedWords, ShareClass shareClass) {
        Set<String> left = typedWords.stream()
                .filter(typed -> !isIdentifierPrefix(typed, shareClass))
                .flatMap(typed -> words(typed).stream())
                .collect(Collectors.toCollection(HashSet::new));
        left.removeAll(words(shareClass.fundName() + " " + shareClass.umbrellaName().orElse("")));
        return left;
    }

    /**
     * The fields searched word by word: fund, umbrella, class, currency, fund regime and the bank's own code
     * (contracts/share-classes.md, "Read by: matcher"; screens.md, Search).
     */
    private static Set<String> textWords(ShareClass shareClass) {
        String fundType = shareClass.fundType().map(type -> type.name().replace('_', ' ')).orElse("");
        return words(String.join(" ", shareClass.fundName(), shareClass.umbrellaName().orElse(""),
                shareClass.shareClassName(), shareClass.currency(), fundType,
                shareClass.internalCode().orElse("")));
    }

    /** The fields matched exactly or by prefix. */
    private static Stream<String> identifiers(ShareClass shareClass) {
        return Stream.of(Optional.of(shareClass.isin().value()), shareClass.valor())
                .flatMap(Optional::stream)
                .map(ShareClassMatcher::compact);
    }

    private static Set<String> words(String text) {
        return Arrays.stream(NOT_ALPHANUMERIC.split(normalise(text)))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String compact(String text) {
        return NOT_ALPHANUMERIC.matcher(normalise(text)).replaceAll("");
    }

    private static String normalise(String text) {
        String withoutAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }
}
