package com.fundgate.shareclasscatalog;

import com.fundgate.shareclasses.ShareClass;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    /** A one-character prefix would match whole countries of ISINs, so it is read as a word only. */
    private static final int MIN_IDENTIFIER_PREFIX = 2;

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ShareClassMatcher() {
    }

    /**
     * The Share Classes, in the order given, that the text names. When the text names some classes in full (every
     * word of the class name), only those are kept, and of those only the most specific: "BNP Bonds CHF A" gives
     * class A, not also class A AC. A fund named without a class gives all its classes.
     */
    public static List<ShareClass> match(String text, List<ShareClass> shareClasses) {
        List<String> typedWords = Arrays.stream(WHITESPACE.split(text.strip()))
                .filter(typed -> !compact(typed).isEmpty())
                .toList();
        if (typedWords.isEmpty()) {
            return List.of();
        }
        List<ShareClass> matches = shareClasses.stream()
                .filter(shareClass -> typedWords.stream().allMatch(typed -> wordMatches(typed, shareClass)))
                .toList();
        return namedInFull(text, matches);
    }

    private static boolean wordMatches(String typed, ShareClass shareClass) {
        String identifierPrefix = compact(typed);
        if (identifierPrefix.length() >= MIN_IDENTIFIER_PREFIX && identifiers(shareClass).anyMatch(
                identifier -> identifier.startsWith(identifierPrefix))) {
            return true;
        }
        return textWords(shareClass).containsAll(words(typed));
    }

    private static List<ShareClass> namedInFull(String text, List<ShareClass> matches) {
        Set<String> queryWords = words(text);
        List<Set<String>> fullyNamed = matches.stream()
                .map(shareClass -> words(shareClass.shareClassName()))
                .filter(queryWords::containsAll)
                .toList();
        if (fullyNamed.isEmpty()) {
            return matches;
        }
        return matches.stream()
                .filter(shareClass -> {
                    Set<String> className = words(shareClass.shareClassName());
                    return fullyNamed.contains(className) && fullyNamed.stream().noneMatch(
                            other -> other.size() > className.size() && other.containsAll(className));
                })
                .toList();
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
