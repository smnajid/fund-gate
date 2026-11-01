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

    /** {@code page} is 1-based, a page of the original document. */
    static boolean onPage(String quote, List<String> pages, int page) {
        String wanted = normalise(quote);
        if (wanted.isEmpty() || page < 1 || page > pages.size()) {
            return false;
        }
        return normalise(pages.get(page - 1)).contains(wanted);
    }

    /** Typographic quotes and dashes as their plain forms, no whitespace, lower case. */
    static String normalise(String text) {
        String plain = text.replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-');
        return WHITESPACE.matcher(plain).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
