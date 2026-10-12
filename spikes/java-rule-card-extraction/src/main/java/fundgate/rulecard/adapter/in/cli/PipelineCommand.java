package fundgate.rulecard.adapter.in.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import fundgate.rulecard.PipelineProperties;
import fundgate.rulecard.application.port.in.DraftRuleCard;
import fundgate.rulecard.application.port.out.FundDocuments;
import fundgate.rulecard.domain.PageSelection;
import fundgate.rulecard.spike.GoldScorer;
import fundgate.rulecard.spike.PageRecall;

/**
 * Inbound adapter. Usage (see run.sh):
 * <pre>
 *   --pages=3,5,16,123 --label=gold-pages   extract, then score against the gold card
 *   --dry-run                               page selection only, no LLM
 *   --score[=runs/x.json]                   score a run (default: latest)
 *   --recall[=page-recall-key.json] [--v2]  page recall on unseen documents (ticket #32), no LLM
 *   --sweep                                 every Nordea 1 sub-fund against the shared rule pages (ticket #32)
 *   --check-gold                            every gold quote on its page, and auto scope covers gold pages
 * </pre>
 */
@Component
class PipelineCommand implements ApplicationRunner {

    private final DraftRuleCard draftRuleCard;
    private final FundDocuments documents;
    private final GoldScorer scorer;
    private final PipelineProperties props;

    PipelineCommand(DraftRuleCard draftRuleCard, FundDocuments documents, GoldScorer scorer, PipelineProperties props) {
        this.draftRuleCard = draftRuleCard;
        this.documents = documents;
        this.scorer = scorer;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var fund = props.fund();
        int maxPages = Integer.parseInt(opt(args, "max-pages", "40"));

        if (args.containsOption("check-gold")) {
            scorer.checkGold();
            return;
        }
        if (args.containsOption("score")) {
            var given = opt(args, "score", "");
            var run = given.isBlank() ? latestRun() : Path.of(given);
            System.out.println(scorer.score(run));
            return;
        }
        if (args.containsOption("recall")) {
            var given = opt(args, "recall", "");
            PageRecall.run(documents, Path.of(given.isBlank() ? "page-recall-key.json" : given), maxPages,
                    args.containsOption("v2"));
            return;
        }
        if (args.containsOption("sweep")) {
            PageRecall.sweep(documents, fund.prospectus(), Path.of("nordea1-subfunds.json"), List.of(3, 5, 123, 124, 138), maxPages,
                    args.containsOption("v2"));
            return;
        }
        if (args.containsOption("dry-run")) {
            var selected = PageSelection.select(documents.load(fund.prospectus()), fund.name(), maxPages);
            System.out.printf("scope: %d pages: %s%n", selected.size(), selected);
            return;
        }

        var cmd = new DraftRuleCard.Command(
                fund.displayName(),
                fund.name(),
                fund.shareClasses(),
                fund.prospectus(),
                fund.kids(),
                pages(opt(args, "pages", "")),
                maxPages,
                opt(args, "label", "auto"));
        draftRuleCard.draft(cmd);
        System.out.println();
        System.out.println(scorer.score(latestRun()));
    }

    private Path latestRun() throws java.io.IOException {
        try (var files = Files.list(props.runsDir())) {
            return files.filter(f -> f.toString().endsWith(".json")).max(Comparator.naturalOrder()).orElseThrow();
        }
    }

    private static String opt(ApplicationArguments args, String name, String fallback) {
        var v = args.getOptionValues(name);
        return v == null || v.isEmpty() ? fallback : v.getFirst();
    }

    private static List<Integer> pages(String csv) {
        return csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }
}
