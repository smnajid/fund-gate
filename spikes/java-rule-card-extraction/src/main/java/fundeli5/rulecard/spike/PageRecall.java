package fundeli5.rulecard.spike;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.application.port.out.PageRanking;
import fundeli5.rulecard.domain.QuoteCheck;

/**
 * PROTOTYPE, tickets #32 and #39. Page recall of the {@link PageRanking} on documents it was not tuned on. The answer key
 * ({@code page-recall-key.json}) lists, per document, the page holding each Hard Restriction plus a short quote;
 * the quote is checked on its page first, so a wrong key shows up as KEY? rather than as a selection miss.
 */
public final class PageRecall {

    /** {@code alt}: other pages that state the same rule just as well; a hit on any of them counts. */
    record Expect(String kind, int page, String quote, List<Integer> alt) {

        boolean hit(java.util.Set<Integer> selected) {
            return selected.contains(page) || (alt != null && alt.stream().anyMatch(selected::contains));
        }
    }

    record Doc(String doc, String subFund, List<Expect> rules) {
    }

    private PageRecall() {
    }

    public static void run(FundDocuments documents, PageRanking ranking, Path key, int maxPages) {
        List<Doc> docs = JsonMapper.builder().build().readValue(key.toFile(), new TypeReference<List<Doc>>() {
        });
        int hitAll = 0;
        int all = 0;
        for (var d : docs) {
            var doc = documents.load(d.doc());
            int chars = doc.pages().stream().mapToInt(String::length).sum();
            if (chars < 300 * doc.pages().size()) {
                System.out.printf("%n== %s (%d pp): NO TEXT LAYER, %d chars/page, needs OCR%n", d.doc(),
                        doc.pages().size(), chars / Math.max(1, doc.pages().size()));
                continue;
            }
            var selected = new TreeSet<>(ranking.select(doc, d.subFund(), maxPages));
            int hit = (int) d.rules().stream().filter(r -> r.hit(selected)).count();
            hitAll += hit;
            all += d.rules().size();
            System.out.printf("%n== %s (%d pp), sub-fund \"%s\": selected %d pages, rule recall %d/%d%n", d.doc(),
                    doc.pages().size(), d.subFund(), selected.size(), hit, d.rules().size());
            System.out.printf("   selected: %s%n", selected);
            for (var r : d.rules()) {
                var keyOk = r.quote() == null || QuoteCheck.norm(doc.page(r.page())).contains(QuoteCheck.norm(r.quote()));
                System.out.printf("   %-4s %-5s p.%-3d %-26s %s%n", r.hit(selected) ? "ok" : "MISS",
                        keyOk ? "" : "KEY?", r.page(), r.kind(), r.quote() == null ? "" : '"' + r.quote() + '"');
            }
        }
        System.out.printf("%nTOTAL rule recall %d/%d%n", hitAll, all);
    }
}
