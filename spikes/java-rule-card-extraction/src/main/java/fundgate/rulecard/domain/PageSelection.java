package fundgate.rulecard.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Picks the pages of an umbrella prospectus that matter for one sub-fund: its own description page, every
 * page naming it (fee tables), plus general pages scored by restriction keywords. Other sub-funds'
 * description pages are excluded. Same heuristic as the Python spike.
 */
public final class PageSelection {

    private static final List<Pattern> KEYWORDS = List.of(
            "US person", "who can invest", "institutional investors", "minimum initial", "core share class", "eligib",
            "Regulation S", "qualified investor", "well-informed", "professional investor",
            "not be (directly or indirectly )?offered", "reject", "restrict")
            .stream().map(k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE)).toList();

    private static final int FRONT_MATTER = 5;

    private PageSelection() {
    }

    public static List<Integer> select(FundDocument prospectus, String subFund, int maxPages) {
        var fund = QuoteCheck.norm(subFund);
        var chosen = new TreeSet<Integer>();
        record Scored(int score, int page) {
        }
        var scored = new ArrayList<Scored>();
        // ticket #32: front matter holds who-can-invest, definitions and (CH) the FINMA exemptions
        for (int i = 1; i <= Math.min(FRONT_MATTER, prospectus.pages().size()); i++) {
            chosen.add(i);
        }
        boolean ownPage = false;
        for (int i = 1; i <= prospectus.pages().size(); i++) {
            var text = prospectus.page(i);
            var all = QuoteCheck.norm(text);
            if (QuoteCheck.norm(head(text, 400)).contains(fund) && all.contains("investmentobjective")) {
                chosen.add(i);
                ownPage = true;
                continue;
            }
            if (QuoteCheck.norm(head(text, 600)).contains("investmentobjectiveandpolicy")) {
                continue;
            }
            if (all.contains(fund)) {
                chosen.add(i);
            }
            int score = KEYWORDS.stream().mapToInt(k -> (int) k.matcher(text).results().count()).sum();
            if (score >= 2) {
                scored.add(new Scored(score, i));
            }
        }
        if (!ownPage && prospectus.pages().size() > 100) {
            // ticket #32: a fund name that doesn't match the prospectus silently drops the description page
            throw new IllegalStateException("no description page for sub-fund '" + subFund + "' in " + prospectus.id());
        }
        scored.sort((a, b) -> a.score() != b.score() ? b.score() - a.score() : b.page() - a.page());
        for (var s : scored) {
            if (chosen.size() >= maxPages) {
                break;
            }
            chosen.add(s.page());
        }
        return List.copyOf(chosen);
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
