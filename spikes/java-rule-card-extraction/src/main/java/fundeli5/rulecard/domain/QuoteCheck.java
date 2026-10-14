package fundeli5.rulecard.domain;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Every quote must be found on the page it cites. Whitespace-insensitive, because PDF text extraction
 * breaks lines inside phrases and around quote marks. This is what catches a made-up quote, with or
 * without the Citations API.
 */
public final class QuoteCheck {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    private QuoteCheck() {
    }

    public static String norm(String s) {
        var t = s.replace('’', '\'').replace('“', '"').replace('”', '"').replace('–', '-').replace('—', '-');
        return WHITESPACE.matcher(t).replaceAll("").toLowerCase();
    }

    public static Rule verify(Rule rule, Map<String, FundDocument> docs) {
        return rule.withCitations(c -> c.verified(onPage(c, docs.get(c.doc()))));
    }

    private static boolean onPage(Citation c, FundDocument doc) {
        if (doc == null || c.quote() == null || c.pages() == null || c.pages().isEmpty()) {
            return false;
        }
        var text = new StringBuilder();
        c.pages().forEach(p -> text.append(doc.page(p)).append(' '));
        return !text.isEmpty() && norm(text.toString()).contains(norm(c.quote()));
    }
}
