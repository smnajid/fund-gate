package fundgate.rulecard.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fundgate.rulecard.application.port.in.DraftRuleCard;
import fundgate.rulecard.application.port.out.ExtractionRuns;
import fundgate.rulecard.application.port.out.FundDocuments;
import fundgate.rulecard.application.port.out.RuleCardDrafter;
import fundgate.rulecard.application.port.out.RuleCardDrafter.Pdf;
import fundgate.rulecard.domain.FundDocument;
import fundgate.rulecard.domain.PageSelection;
import fundgate.rulecard.domain.QuoteCheck;
import fundgate.rulecard.domain.RuleCard;
import fundgate.rulecard.domain.RuleKind;

/**
 * Page selection, then the drafter, then the quote-on-page check. Always ends in a Draft card.
 * Framework-free: wired in {@code PipelineWiring}.
 */
public class DraftRuleCardService implements DraftRuleCard {

    private final FundDocuments documents;
    private final RuleCardDrafter drafter;
    private final ExtractionRuns runs;

    public DraftRuleCardService(FundDocuments documents, RuleCardDrafter drafter, ExtractionRuns runs) {
        this.documents = documents;
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
                ? PageSelection.select(prospectus, cmd.subFund(), cmd.maxPages())
                : cmd.pages();
        System.out.printf("scope: %d of %d prospectus pages: %s%n", selected.size(), prospectus.pages().size(), selected);

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
        System.out.printf("quotes verified on their page: %d/%d%n", card.verifiedQuotes(), card.quotes());
        System.out.printf("time %.1fs, cost $%.4f%n", run.totalSeconds(), run.totalUsd());
        System.out.printf("saved %s%n", path);
    }
}
