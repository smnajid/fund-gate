package fundgate.rulecard.spike;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import fundgate.rulecard.domain.FundDocument;
import fundgate.rulecard.domain.QuoteCheck;

/**
 * PROTOTYPE, ticket #32. A layout-free, EN/DE/FR variant of {@link fundgate.rulecard.domain.PageSelection}, written
 * after v1 failed on 20 unseen prospectuses: front matter, the sub-fund's own section (first page naming it near the
 * top, plus the pages after it), the pages naming it most, then pages scored by how many distinct multilingual restriction keywords they contain.
 */
public final class PageSelectionV2 {

    private static final List<Pattern> KEYWORDS = List.of(
            // US person
            "U\\.?\\s?S\\.?[ -]?Person", "personnes? américaine", "ressortissants? américain", "Vereinigten Staaten",
            // investor eligibility
            "institutional investors?", "investisseurs? institutionnels?", "institutionelle[nr]? Anleger",
            "qualified investors?", "investisseurs? qualifiés?", "qualifizierte[nr]? Anleger", "well-informed",
            "professional (investors?|clients?)", "eligible investors?", "zulässige[nr]? Anleger", "Anlegerart",
            "Anlegerkreis", "(reserved|available) (only )?(to|for)", "réservée?s? aux", "exclusivement",
            // minimums
            "minimum (initial )?(subscription|investment|holding)", "Mindest(erst)?(anlage|zeichnung)",
            "(souscription|investissement|détention) (initiale? )?minim", "montants? minim",
            // v1's keywords, translated: who can invest, core classes, Regulation S, rejections, restrictions
            "who can invest", "qui peut investir", "wer (kann|darf) (investieren|anlegen)", "core share class",
            "eligib", "éligib", "Regulation S", "Règlement S", "Regulierung S",
            "not be (directly or indirectly )?offered", "reject", "rejeter", "refuser", "ablehnen", "zurückweisen",
            "restrict", "beschränk",
            // restrictions
            "selling restrictions", "restrictions de vente", "Verkaufsbeschränkung", "may not be (offered|sold)")
            .stream().map(k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE)).toList();

    /** Section headings that follow a sub-fund's name where its own description starts (normalised text). */
    private static final Pattern OBJECTIVE = Pattern.compile(
            "objective|anlageziel|anlagepolitik|objectif|politiqued.investissement|factsheet|fichetechnique|"
                    + "referencecurrency|basecurrency|devisederéférence|referenzwährung");

    private static final int FRONT_MATTER = 5;
    private static final int SECTION_PAGES = 4;
    private static final int NAME_PAGES = 10;

    private PageSelectionV2() {
    }

    public static List<Integer> select(FundDocument doc, String subFund, int maxPages) {
        int n = doc.pages().size();
        var fund = QuoteCheck.norm(subFund);
        var chosen = new TreeSet<Integer>();
        for (int i = 1; i <= Math.min(FRONT_MATTER, n); i++) {
            chosen.add(i);
        }
        record Scored(int score, int page) {
        }
        var named = new ArrayList<Scored>();
        var scored = new ArrayList<Scored>();
        int section = 0;
        for (int i = 1; i <= n; i++) {
            var text = doc.page(i);
            var all = QuoteCheck.norm(text);
            int mentions = count(all, fund);
            if (mentions > 0) {
                named.add(new Scored(mentions, i));
                if (section == 0 && i > FRONT_MATTER && opensSection(all, fund)) {
                    section = i;
                }
            }
            // distinct keywords, not occurrences: a table repeating "restrict" on every page must not crowd out
            // the page that names US persons, minimums and institutional investors once each
            int score = (int) KEYWORDS.stream().filter(k -> k.matcher(text).find()).count();
            if (score >= 2) {
                scored.add(new Scored(score, i));
            }
        }
        if (section == 0) {
            section = named.stream().filter(s -> s.page() > FRONT_MATTER).max(Comparator.comparingInt(Scored::score))
                    .map(Scored::page).orElse(0);
        }
        if (section > 0) {
            for (int i = section; i < section + SECTION_PAGES && i <= n; i++) {
                chosen.add(i);
            }
        }
        named.sort(Comparator.comparingInt(Scored::score).reversed());
        named.stream().limit(NAME_PAGES).forEach(s -> chosen.add(s.page()));
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::page));
        for (var s : scored) {
            if (chosen.size() >= maxPages) {
                break;
            }
            chosen.add(s.page());
        }
        return List.copyOf(chosen);
    }

    private static boolean opensSection(String page, String fund) {
        for (int i = page.indexOf(fund); i >= 0; i = page.indexOf(fund, i + 1)) {
            var after = page.substring(i + fund.length(), Math.min(page.length(), i + fund.length() + 120));
            if (OBJECTIVE.matcher(after).find()) {
                return true;
            }
        }
        return false;
    }

    private static int count(String haystack, String needle) {
        int c = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            c++;
        }
        return c;
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
