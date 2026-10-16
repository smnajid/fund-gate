package fundeli5.rulecard.spike;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

import fundeli5.rulecard.domain.FundDocument;

/**
 * PROTOTYPE, ticket #39. Ranks a document's pages per rule kind with Jev, two ways, and selects pages from the
 * rankings the way the Elasticsearch prototype did (front matter, then round-robin over the rule kinds), so the
 * budgets compare with {@link PageSelectionV2}.
 * <ul>
 * <li>{@code choice}: per rule kind, a choice question whose options are the pages (plus "none"). A question holds
 * about 20 pages (32k tokens), so the pages run as a knockout: the top 3 of each batch go to the next round until one
 * batch is left.</li>
 * <li>{@code page}: per page, one noul question per rule kind (JEV's own RAG-classification pattern). Scores compare
 * across the whole document.</li>
 * </ul>
 * Rankings are cached in {@code jev-rankings/}, so changing the budget costs nothing.
 */
public final class JevPageRanking {

    public enum Method {
        CHOICE, PAGE
    }

    /** Rule kinds asked about. Instructions are English; the pages may be EN, DE or FR. */
    private static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put("description", "Is this page the start of, or part of, the own section of the sub-fund \"%s\" "
                + "(its investment objective, reference currency, share classes or fact sheet)?");
        RULES.put("us-person", "Does this page restrict or prohibit US Persons (citizens or residents of the United "
                + "States) from subscribing to or holding shares, or define who counts as a US Person?");
        RULES.put("investor-eligibility", "Does this page say that a share class, or the fund, is reserved for or "
                + "restricted to certain investors (for example institutional, professional, qualified or well-informed "
                + "investors, or clients with a discretionary management mandate)?");
        RULES.put("minimum", "Does this page state a minimum initial subscription, minimum investment or minimum "
                + "holding amount for share classes?");
        RULES.put("target-market", "Does this page describe the target market or the profile of the typical investor "
                + "(client type, knowledge and experience, ability to bear losses, risk tolerance, investment horizon)?");
        RULES.put("other-restriction", "Does this page state another restriction on who may subscribe to or hold "
                + "shares (selling restrictions in some countries, the right to reject subscriptions, compulsory "
                + "redemption or conversion of ineligible holders)?");
    }

    private static final int FRONT_MATTER = 5;
    /** Characters of page text sent per page; longer pages (big tables) are cut. */
    private static final int PAGE_CHARS = 6000;
    /** Characters of page text per choice question, about 22k tokens of the 32k per-question limit. */
    private static final int CHOICE_CHARS = 66_000;
    private static final int KEEP_PER_BATCH = 3;

    record Scored(int page, double score) {
    }

    private final JevClient jev;
    private final Path cacheDir;

    public JevPageRanking(JevClient jev, Path cacheDir) {
        this.jev = jev;
        this.cacheDir = cacheDir;
    }

    /** Pages within budget: everything for a small document, else front matter then round-robin over rule kinds. */
    public List<Integer> select(FundDocument doc, String subFund, int maxPages, Method method) {
        int n = doc.pages().size();
        if (n <= maxPages) {
            return java.util.stream.IntStream.rangeClosed(1, n).boxed().toList();
        }
        var rankings = rankings(doc, subFund, method);
        var chosen = new TreeSet<Integer>();
        for (int i = 1; i <= FRONT_MATTER; i++) {
            chosen.add(i);
        }
        var cursors = new int[rankings.size()];
        var lists = new ArrayList<>(rankings.values());
        boolean progress = true;
        while (chosen.size() < maxPages && progress) {
            progress = false;
            for (int r = 0; r < lists.size() && chosen.size() < maxPages; r++) {
                var list = lists.get(r);
                while (cursors[r] < list.size() && chosen.contains(list.get(cursors[r]).page())) {
                    cursors[r]++;
                }
                if (cursors[r] < list.size()) {
                    chosen.add(list.get(cursors[r]++).page());
                    progress = true;
                }
            }
        }
        return List.copyOf(chosen);
    }

    /** Per rule kind, every page ranked best first. Cached per document, sub-fund, method and model. */
    public Map<String, List<Scored>> rankings(FundDocument doc, String subFund, Method method) {
        var file = cacheDir.resolve("%s.%s.%s.json".formatted(doc.id(), method.name().toLowerCase(), jev.model()));
        if (Files.exists(file)) {
            return jev.json().readValue(file.toFile(), new TypeReference<LinkedHashMap<String, List<Scored>>>() {
            });
        }
        long t = System.nanoTime();
        var result = method == Method.CHOICE ? choice(doc, subFund) : perPage(doc, subFund);
        try {
            Files.createDirectories(cacheDir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        jev.json().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), result);
        System.out.printf("   jev %s on %s: %.0fs, running total %s%n", method.name().toLowerCase(), doc.id(),
                (System.nanoTime() - t) / 1e9, jev.usage());
        return result;
    }

    // ---- per page: one request per page, one noul per rule kind

    private Map<String, List<Scored>> perPage(FundDocument doc, String subFund) {
        var questions = jev.json().createObjectNode();
        RULES.forEach((id, text) -> {
            var q = questions.putObject(id);
            q.put("type", "noul");
            q.put("instructions", text.formatted(subFund));
        });
        int n = doc.pages().size();
        var futures = new ArrayList<Future<Map<String, Double>>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int p = 1; p <= n; p++) {
                int page = p;
                futures.add(pool.submit(() -> {
                    var answers = jev.ask(pageState(doc, page, subFund), questions.deepCopy());
                    var scores = new LinkedHashMap<String, Double>();
                    RULES.keySet().forEach(id -> scores.put(id, answers.path(id).path("noul").asDouble()));
                    return scores;
                }));
            }
        }
        var result = new LinkedHashMap<String, List<Scored>>();
        RULES.keySet().forEach(id -> result.put(id, new ArrayList<>()));
        for (int p = 1; p <= n; p++) {
            var scores = get(futures.get(p - 1));
            int page = p;
            scores.forEach((id, s) -> result.get(id).add(new Scored(page, s)));
        }
        result.values().forEach(l -> l.sort(Comparator.comparingDouble(Scored::score).reversed()));
        return result;
    }

    private static String pageState(FundDocument doc, int page, String subFund) {
        return "Fund document \"%s\" (an umbrella or fund prospectus, KID or fund contract), page %d of %d. "
                .formatted(doc.title(), page, doc.pages().size())
                + "Sub-fund of interest: \"%s\". The page may be in English, German or French.\n\n".formatted(subFund)
                + cut(doc.page(page));
    }

    // ---- choice: per rule kind, a knockout over batches of pages

    private Map<String, List<Scored>> choice(FundDocument doc, String subFund) {
        var result = new LinkedHashMap<String, List<Scored>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<String, Future<List<Scored>>>();
            RULES.forEach((id, text) -> futures.put(id, pool.submit(() -> knockout(doc, subFund, text.formatted(subFund)))));
            futures.forEach((id, f) -> result.put(id, get(f)));
        }
        return result;
    }

    /**
     * Round 1 covers every page; the top {@value #KEEP_PER_BATCH} of each batch go on, until one batch is left.
     * Ranking: pages that reached a later round first, then by probability in the last round they played.
     */
    private List<Scored> knockout(FundDocument doc, String subFund, String rule) {
        record Reached(int page, int round, double p) {
        }
        var reached = new LinkedHashMap<Integer, Reached>();
        var candidates = new ArrayList<Integer>();
        for (int p = 1; p <= doc.pages().size(); p++) {
            candidates.add(p);
        }
        for (int round = 1; ; round++) {
            var batches = batches(doc, candidates);
            var next = new ArrayList<Integer>();
            var answered = new ArrayList<Future<Map<Integer, Double>>>();
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                batches.forEach(batch -> answered.add(pool.submit(() -> ask(doc, subFund, rule, batch))));
            }
            for (var future : answered) {
                var probs = get(future);
                int r = round;
                probs.forEach((page, p) -> reached.put(page, new Reached(page, r, p)));
                probs.entrySet().stream().sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                        .limit(KEEP_PER_BATCH).forEach(e -> next.add(e.getKey()));
            }
            if (batches.size() == 1) {
                break;
            }
            next.sort(Comparator.naturalOrder());
            candidates = next;
        }
        return reached.values().stream()
                .sorted(Comparator.comparingInt(Reached::round).reversed()
                        .thenComparing(Comparator.comparingDouble(Reached::p).reversed()))
                .map(r -> new Scored(r.page(), r.round() + r.p()))
                .toList();
    }

    /** One choice question over the batch; splits it in two if Jev says it is too long. */
    private Map<Integer, Double> ask(FundDocument doc, String subFund, String rule, List<Integer> batch) {
        var questions = jev.json().createObjectNode();
        ObjectNode q = questions.putObject("page");
        q.put("type", "choice");
        q.put("instructions", "Which page of this fund document answers yes to the following question? " + rule
                + " Pages may be in English, German or French. Choose \"none\" if no page does.");
        var criteria = q.putObject("criteria");
        batch.forEach(p -> criteria.put("p" + p, cut(doc.page(p))));
        criteria.put("none", "None of these pages answers yes.");
        var state = "Fund document \"%s\", %d pages. Sub-fund of interest: \"%s\". The options are pages of the document."
                .formatted(doc.title(), doc.pages().size(), subFund);
        try {
            var probs = jev.ask(state, questions).path("page").path("probabilities");
            var out = new LinkedHashMap<Integer, Double>();
            batch.forEach(p -> out.put(p, probs.path("p" + p).asDouble()));
            return out;
        } catch (JevClient.TooLong e) {
            if (batch.size() < 2) {
                throw e;
            }
            var out = new LinkedHashMap<Integer, Double>();
            out.putAll(ask(doc, subFund, rule, batch.subList(0, batch.size() / 2)));
            out.putAll(ask(doc, subFund, rule, batch.subList(batch.size() / 2, batch.size())));
            return out;
        }
    }

    private static List<List<Integer>> batches(FundDocument doc, List<Integer> pages) {
        var out = new ArrayList<List<Integer>>();
        var current = new ArrayList<Integer>();
        int chars = 0;
        for (int p : pages) {
            int len = cut(doc.page(p)).length();
            if (!current.isEmpty() && (chars + len > CHOICE_CHARS || current.size() >= 250)) {
                out.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(p);
            chars += len;
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    private static String cut(String page) {
        return page.length() <= PAGE_CHARS ? page : page.substring(0, PAGE_CHARS);
    }

    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        }
    }
}
