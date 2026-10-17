package fundeli5.rulecard.adapter.in.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import fundeli5.rulecard.PipelineProperties;
import fundeli5.rulecard.application.port.in.DraftRuleCard;
import fundeli5.rulecard.application.DraftRuleCardService;
import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.application.port.out.PageRanking;
import fundeli5.rulecard.spike.GoldScorer;
import fundeli5.rulecard.spike.PageRecall;

/**
 * Inbound adapter. Usage (see run.sh):
 * <pre>
 *   [--max-pages=25] --label=auto           extract from the pages Jev choice selects, with the coverage check (#39)
 *   --pages=3,5,16,123 --label=gold-pages   extract from these pages, then score against the gold card
 *   --dry-run                               page ranking and coverage only, no LLM
 *   --score[=runs/x.json]                   score a run (default: latest)
 *   --recall[=page-recall-key.json]         page recall on unseen documents (tickets #32, #39), no LLM
 *   --check-gold                            every gold quote on its page, and Jev's 25 pages cover the gold pages
 * Jev needs TYPESAFE_API_KEY unless the ranking is cached (fundeli5.jev in application.yml).
 * </pre>
 */
@Component
class PipelineCommand implements ApplicationRunner {

    private final DraftRuleCard draftRuleCard;
    private final FundDocuments documents;
    private final PageRanking ranking;
    private final GoldScorer scorer;
    private final PipelineProperties props;

    PipelineCommand(DraftRuleCard draftRuleCard, FundDocuments documents, PageRanking ranking, GoldScorer scorer,
            PipelineProperties props) {
        this.draftRuleCard = draftRuleCard;
        this.documents = documents;
        this.ranking = ranking;
        this.scorer = scorer;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var fund = props.fund();
        int maxPages = Integer.parseInt(opt(args, "max-pages", "25"));

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
            PageRecall.run(documents, ranking, Path.of(given.isBlank() ? "page-recall-key.json" : given), maxPages);
            return;
        }
        if (args.containsOption("dry-run")) {
            var prospectus = documents.load(fund.prospectus());
            var selected = ranking.select(prospectus, fund.name(), maxPages);
            System.out.printf("scope: %d pages: %s%n", selected.size(), selected);
            DraftRuleCardService.printCoverage(ranking.coverage(prospectus, fund.name(), selected));
            return;
        }

        var pages = pages(opt(args, "pages", ""));
        var cmd = new DraftRuleCard.Command(
                fund.displayName(),
                fund.name(),
                fund.shareClasses(),
                fund.prospectus(),
                fund.kids(),
                pages,
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
