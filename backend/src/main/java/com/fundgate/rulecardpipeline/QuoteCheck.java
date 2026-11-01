package com.fundgate.rulecardpipeline;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Every quote must be found on the page it cites: this is what catches a made-up quote. Whitespace-insensitive,
 * because PDF text extraction breaks lines inside phrases, and French typesetting uses no-break spaces.
 */
final class QuoteCheck {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    private QuoteCheck() {
    }

    /** Shorter quotes, like "EUR" or "the", are found on almost any page, so they prove nothing. */
    static final int MIN_WORDS = 3;

    /** {@code page} is 1-based, a page of the original document. Words stay apart: "US persons" is not "USpersons". */
    static boolean onPage(String quote, List<String> pages, int page) {
        String wanted = plain(quote);
        if (wanted.isEmpty() || wanted.split(" ").length < MIN_WORDS || page < 1 || page > pages.size()) {
            return false;
        }
        String text = plain(pages.get(page - 1));
        for (int at = text.indexOf(wanted); at >= 0; at = text.indexOf(wanted, at + 1)) {
            if (!wordAt(text, at - 1) && !wordAt(text, at + wanted.length())) {
                return true;
            }
        }
        return false;
    }

    private static boolean wordAt(String text, int index) {
        return index >= 0 && index < text.length() && Character.isLetterOrDigit(text.charAt(index));
    }

    /** As {@link #plain}, with no whitespace at all: for finding a name the layout may have split oddly. */
    static String normalise(String text) {
        return plain(text).replace(" ", "");
    }

    /** Typographic quotes and dashes as their plain forms, each run of whitespace one space, lower case. */
    private static String plain(String text) {
        String plain = text.replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-');
        return WHITESPACE.matcher(plain).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
