package fundeli5.rulecard.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fundeli5.rulecard.application.port.in.DraftRuleCard;
import fundeli5.rulecard.application.port.out.ExtractionRuns;
import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.application.port.out.PageRanking;
import fundeli5.rulecard.application.port.out.RuleCardDrafter;
import fundeli5.rulecard.application.port.out.RuleCardDrafter.Pdf;
import fundeli5.rulecard.domain.FundDocument;
import fundeli5.rulecard.domain.QuoteCheck;
import fundeli5.rulecard.domain.RuleCard;
import fundeli5.rulecard.domain.RuleKind;

/**
 * Page ranking, then the drafter, then the quote-on-page check. Always ends in a Draft card.
 * Framework-free: wired in {@code PipelineWiring}.
 */
public class DraftRuleCardService implements DraftRuleCard {

    private final FundDocuments documents;
    private final PageRanking ranking;
    private final RuleCardDrafter drafter;
    private final ExtractionRuns runs;

    public DraftRuleCardService(FundDocuments documents, PageRanking ranking, RuleCardDrafter drafter,
            ExtractionRuns runs) {
        this.documents = documents;
        this.ranking = ranking;
        this.drafter = drafter;
        this.runs = runs;
    }

    @Override
    public ExtractionRun draft(Command cmd) {
        var docs = new LinkedHashMap<String, FundDocument>();
        docs.put(cmd.prospectus(), documents.load(cmd.prospectus()));
        cmd.kids().values().forEach(id -> docs.put(id, documents.load(id)));

        var prospectus = docs.get(cmd.prospectus());
        var selected = cmd.pages().isEmpty()
                ? ranking.select(prospectus, cmd.subFund(), cmd.maxPages())
                : cmd.pages();
        System.out.printf("scope: %d of %d prospectus pages: %s%n", selected.size(), prospectus.pages().size(), selected);
        printCoverage(ranking.coverage(prospectus, cmd.subFund(), selected));

        var pdfs = new ArrayList<Pdf>();
        pdfs.add(new Pdf(cmd.prospectus(), prospectus.title() + " (selected pages)", selected,
                documents.excerpt(cmd.prospectus(), selected)));
        cmd.kids().forEach((cls, id) -> {
            var kid = docs.get(id);
            pdfs.add(new Pdf(id, "KID " + cls + " (" + cmd.shareClasses().get(cls) + ")",
                    allPages(kid), documents.whole(id)));
        });

        System.out.printf("drafter: %s%n", drafter.name());
        var draft = drafter.draft(new RuleCardDrafter.Request(cmd.fund(), cmd.shareClasses(), pdfs));
        var rules = draft.rules().stream().map(r -> QuoteCheck.verify(r, docs)).toList();
        var card = RuleCard.draft(cmd.fund(), cmd.shareClasses(), rules);

        var run = new ExtractionRun(cmd.label(), drafter.name(), selected, draft.usage(),
                round(draft.usage().stream().mapToDouble(RuleCardDrafter.Usage::seconds).sum()),
                round(draft.usage().stream().mapToDouble(RuleCardDrafter.Usage::usd).sum()), card);
        var path = runs.save(run);
        print(run, path);
        return run;
    }

    /** Ticket #39: a rule kind whose best selected page scores low goes to the validator as "possibly missed". */
    public static void printCoverage(List<PageRanking.Coverage> coverage) {
        System.out.println("coverage:");
        coverage.forEach(c -> System.out.printf("  %-22s best selected %s%s%s%n", c.kind(),
                c.bestSelected() == null ? "none" : "p.%d %.2f".formatted(c.bestSelected().page(), c.bestSelected().score()),
                c.bestLeftOut() == null ? "" : ", best left out p.%d %.2f".formatted(c.bestLeftOut().page(), c.bestLeftOut().score()),
                c.possiblyMissed() ? "  POSSIBLY MISSED" : ""));
    }

    private static List<Integer> allPages(FundDocument doc) {
        return java.util.stream.IntStream.rangeClosed(1, doc.pages().size()).boxed().toList();
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }

    private static void print(ExtractionRun run, java.nio.file.Path path) {
        var card = run.card();
        Map<RuleKind, Long> byKind = new LinkedHashMap<>();
        card.rules().forEach(r -> byKind.merge(r.kind(), 1L, Long::sum));
        System.out.printf("%nRule Card (%s): %d rules %s%n", card.status(), card.rules().size(), byKind);
        System.out.printf("unmodelled restrictions suggested Noted: %d of %d (all stay Blocking until validated)%n",
                card.rules().stream().filter(r -> r.kind() == RuleKind.UNMODELLED_RESTRICTION && r.notedReason() != null).count(),
                byKind.getOrDefault(RuleKind.UNMODELLED_RESTRICTION, 0L));
        System.out.printf("proposed Fund Registration entries: %d%n", card.fundRegistration().size());
        System.out.printf("quotes verified on their page: %d/%d%n", card.verifiedQuotes(), card.quotes());
        System.out.printf("time %.1fs, cost $%.4f%n", run.totalSeconds(), run.totalUsd());
        System.out.printf("saved %s%n", path);
    }
}
