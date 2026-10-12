package fundgate.rulecard.spike;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import fundgate.rulecard.application.port.out.FundDocuments;
import fundgate.rulecard.domain.PageSelection;
import fundgate.rulecard.domain.QuoteCheck;

/**
 * PROTOTYPE, ticket #32. Page recall of {@link PageSelection} on documents it was not tuned on. The answer key
 * ({@code page-recall-key.json}) lists, per document, the page holding each Hard Restriction plus a short quote;
 * the quote is checked on its page first, so a wrong key shows up as KEY? rather than as a selection miss.
 */
public final class PageRecall {

    record Expect(String kind, int page, String quote) {
    }

    record Doc(String doc, String subFund, List<Expect> rules) {
    }

    private PageRecall() {
    }

    public static void run(FundDocuments documents, Path key, int maxPages) {
        List<Doc> docs = JsonMapper.builder().build().readValue(key.toFile(), new TypeReference<List<Doc>>() {
        });
        int hitAll = 0;
        int all = 0;
        for (var d : docs) {
            var doc = documents.load(d.doc());
            var selected = new TreeSet<>(PageSelection.select(doc, d.subFund(), maxPages));
            var expected = new TreeSet<Integer>();
            d.rules().forEach(r -> expected.add(r.page()));
            int hit = (int) expected.stream().filter(selected::contains).count();
            hitAll += hit;
            all += expected.size();
            System.out.printf("%n== %s (%d pp), sub-fund \"%s\": selected %d pages, page recall %d/%d%n", d.doc(),
                    doc.pages().size(), d.subFund(), selected.size(), hit, expected.size());
            System.out.printf("   selected: %s%n", selected);
            for (var r : d.rules()) {
                var keyOk = r.quote() == null || QuoteCheck.norm(doc.page(r.page())).contains(QuoteCheck.norm(r.quote()));
                System.out.printf("   %-4s %-5s p.%-3d %-26s %s%n", selected.contains(r.page()) ? "ok" : "MISS",
                        keyOk ? "" : "KEY?", r.page(), r.kind(), r.quote() == null ? "" : '"' + r.quote() + '"');
            }
        }
        System.out.printf("%nTOTAL page recall %d/%d%n", hitAll, all);
    }

    /** Every sub-fund of one umbrella against the shared rule pages plus its own description page (TOC page). */
    public static void sweep(FundDocuments documents, String docId, Path subFunds, List<Integer> shared, int maxPages) {
        record SubFund(String name, int page) {
        }
        List<SubFund> funds = JsonMapper.builder().build().readValue(subFunds.toFile(),
                new TypeReference<List<SubFund>>() {
                });
        var doc = documents.load(docId);
        int complete = 0;
        for (var f : funds) {
            var selected = new TreeSet<>(PageSelection.select(doc, f.name(), maxPages));
            var expected = new TreeSet<>(shared);
            expected.add(f.page());
            var missing = new TreeSet<>(expected);
            missing.removeAll(selected);
            if (missing.isEmpty()) {
                complete++;
            }
            System.out.printf("%-4s %-60s %2d pages  missing %s%n", missing.isEmpty() ? "ok" : "MISS", f.name(),
                    selected.size(), missing);
        }
        System.out.printf("%nSWEEP %d/%d sub-funds with every expected page%n", complete, funds.size());
    }
}
