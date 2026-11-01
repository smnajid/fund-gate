package com.fundgate.rulecardpipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Picks the pages of a long document that matter for one fund, so an umbrella prospectus of hundreds of pages is
 * not sent whole. Ported from the extraction spike's layout-free, EN/DE/FR selection (v2, ticket #32): the front
 * matter, the fund's own section, the pages naming it most, then pages ranked by how many distinct restriction
 * keywords they hold. On the four sample funds it kept every page a gold quote sits on.
 */
final class PageSelection {

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
            // who can invest, core classes, Regulation S, rejections, restrictions
            "who can invest", "qui peut investir", "wer (kann|darf) (investieren|anlegen)", "core share class",
            "eligib", "éligib", "Regulation S", "Règlement S", "Regulierung S",
            "not be (directly or indirectly )?offered", "reject", "rejeter", "refuser", "ablehnen", "zurückweisen",
            "restrict", "beschränk",
            // selling restrictions
            "selling restrictions", "restrictions de vente", "Verkaufsbeschränkung", "may not be (offered|sold)")
            .stream().map(keyword -> Pattern.compile(keyword, Pattern.CASE_INSENSITIVE)).toList();

    /** Section headings that follow a fund's name where its own description starts (normalised text). */
    private static final Pattern OBJECTIVE = Pattern.compile(
            "objective|anlageziel|anlagepolitik|objectif|politiqued.investissement|factsheet|fichetechnique|"
                    + "referencecurrency|basecurrency|devisederéférence|referenzwährung");

    /** Front matter holds who can invest, the definitions and (CH) the FINMA exemptions. */
    private static final int FRONT_MATTER = 5;
    private static final int SECTION_PAGES = 4;
    private static final int NAME_PAGES = 10;

    private PageSelection() {
    }

    /** 1-based page numbers of the original document, in order. A document within the budget is sent whole. */
    static List<Integer> select(List<String> pages, String fundName, int maxPages) {
        int count = pages.size();
        if (count <= maxPages) {
            return IntStream.rangeClosed(1, count).boxed().toList();
        }
        String fund = QuoteCheck.normalise(fundName);
        TreeSet<Integer> chosen = new TreeSet<>();
        IntStream.rangeClosed(1, Math.min(FRONT_MATTER, count)).forEach(chosen::add);

        record Scored(int score, int page) {
        }
        List<Scored> named = new ArrayList<>();
        List<Scored> scored = new ArrayList<>();
        int section = 0;
        for (int page = 1; page <= count; page++) {
            String text = pages.get(page - 1);
            String normalised = QuoteCheck.normalise(text);
            int mentions = occurrences(normalised, fund);
            if (mentions > 0) {
                named.add(new Scored(mentions, page));
                if (section == 0 && page > FRONT_MATTER && opensSection(normalised, fund)) {
                    section = page;
                }
            }
            // Distinct keywords, not occurrences: a table repeating "restrict" on every page must not crowd out
            // the page that names US persons, minimums and institutional investors once each
            int score = (int) KEYWORDS.stream().filter(keyword -> keyword.matcher(text).find()).count();
            if (score >= 2) {
                scored.add(new Scored(score, page));
            }
        }
        if (section == 0) {
            section = named.stream().filter(name -> name.page() > FRONT_MATTER)
                    .max(Comparator.comparingInt(Scored::score)).map(Scored::page).orElse(0);
        }
        for (int page = section; section > 0 && page < section + SECTION_PAGES && page <= count; page++) {
            chosen.add(page);
        }
        named.stream().sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(NAME_PAGES).forEach(name -> chosen.add(name.page()));
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::page));
        for (Scored candidate : scored) {
            if (chosen.size() >= maxPages) {
                break;
            }
            chosen.add(candidate.page());
        }
        return List.copyOf(chosen);
    }

    private static boolean opensSection(String page, String fund) {
        for (int at = page.indexOf(fund); at >= 0; at = page.indexOf(fund, at + 1)) {
            String after = page.substring(at + fund.length(), Math.min(page.length(), at + fund.length() + 120));
            if (OBJECTIVE.matcher(after).find()) {
                return true;
            }
        }
        return false;
    }

    private static int occurrences(String text, String wanted) {
        if (wanted.isEmpty()) {
            return 0;
        }
        int found = 0;
        for (int at = text.indexOf(wanted); at >= 0; at = text.indexOf(wanted, at + 1)) {
            found++;
        }
        return found;
    }
}
