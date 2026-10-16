package fundeli5.rulecard.spike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.PipelineProperties;
import fundeli5.rulecard.application.port.out.FundDocuments;
import fundeli5.rulecard.domain.FundDocument;
import fundeli5.rulecard.domain.PageSelection;
import fundeli5.rulecard.domain.QuoteCheck;

/**
 * PROTOTYPE, spike-only: scores a run against gold-rule-card.json, same metrics as the Python spike's score.py.
 * Rule recall (required gold rules found), value accuracy, citation support (quotes verified on their page and
 * landing on a page the answer key cites), traps (negative rules, bonus contradiction), unexpected rules.
 */
@Component
public class GoldScorer {

    private final JsonMapper json = JsonMapper.builder().build();
    private final FundDocuments documents;
    private final PipelineProperties props;

    public GoldScorer(FundDocuments documents, PipelineProperties props) {
        this.documents = documents;
        this.props = props;
    }

    private record Key(String cls, String kind, String dimension) {
    }

    public String score(Path runFile) throws IOException {
        var run = json.readTree(runFile.toFile());
        var gold = gold();
        var classes = List.copyOf(props.fund().shareClasses().keySet());
        var runRules = run.path("card").path("rules");

        var got = new LinkedHashMap<Key, List<JsonNode>>();
        for (var r : runRules) {
            for (var cls : expand(r, classes)) {
                got.computeIfAbsent(key(r, r, cls), k -> new ArrayList<>()).add(r);
            }
        }

        var rows = new ArrayList<String>();
        int found = 0, correct = 0, required = 0;
        for (var g : gold.path("rules")) {
            if (flag(g, "optional") || flag(g, "negative") || flag(g, "bonus")) {
                continue;
            }
            var goldPages = new HashSet<String>();
            g.path("citations").forEach(c -> goldPages.add(c.path("doc").asString() + "#" + c.path("page").asInt()));
            for (var cls : expand(g, classes)) {
                required++;
                var cands = got.getOrDefault(key(g, g.path("params"), cls), List.of());
                var hit = cands.stream().filter(c -> valueOk(g, c)).findFirst().orElse(cands.isEmpty() ? null : cands.getFirst());
                boolean ok = hit != null && valueOk(g, hit);
                found += hit != null ? 1 : 0;
                correct += ok ? 1 : 0;
                var cites = hit == null ? List.<JsonNode>of() : hit.path("citations").valueStream().toList();
                long verified = cites.stream().filter(c -> c.path("verified").asBoolean(false)).count();
                boolean onGold = cites.stream().anyMatch(c -> goldPages.contains(c.path("doc").asString("") + "#" + c.path("page").asInt(-1)));
                rows.add("| %s | %s | %s | %s | %d/%d | %s | %s |".formatted(g.path("id").asString(), cls,
                        hit != null ? "found" : "MISSING", ok ? "ok" : (hit != null ? "WRONG" : "-"), verified, cites.size(),
                        onGold ? "yes" : "no", hit == null ? "" : hit.path("confidence").asString()));
            }
        }

        var allCites = runRules.valueStream().flatMap(r -> r.path("citations").valueStream()).toList();
        long verified = allCites.stream().filter(c -> c.path("verified").asBoolean(false)).count();
        long uncited = runRules.valueStream().filter(r -> r.path("citations").isEmpty()).count();

        var traps = new ArrayList<String>();
        for (var g : gold.path("rules")) {
            if (flag(g, "negative")) {
                boolean bad = expand(g, classes).stream()
                        .flatMap(cls -> got.getOrDefault(key(g, g.path("params"), cls), List.of()).stream())
                        .anyMatch(r -> g.path("params").propertyStream().allMatch(e -> same(r.get(e.getKey()), e.getValue())));
                traps.add(g.path("id").asString() + ": " + (bad ? "FALSE POSITIVE" : "avoided"));
            }
            if (flag(g, "bonus")) {
                var cls = expand(g, classes);
                boolean flagged = got.entrySet().stream().filter(e -> cls.contains(e.getKey().cls()))
                        .flatMap(e -> e.getValue().stream()).anyMatch(r -> r.path("contradiction").asBoolean());
                traps.add(g.path("id").asString() + ": " + (flagged ? "flagged" : "not flagged"));
            }
        }

        var goldKeys = new HashSet<Key>();
        for (var g : gold.path("rules")) {
            for (var cls : expand(g, classes)) {
                goldKeys.add(key(g, g.path("params"), cls));
            }
        }
        var unexpected = runRules.valueStream()
                .filter(r -> !"unmodelled_restriction".equals(r.path("kind").asString()))
                .filter(r -> expand(r, classes).stream().noneMatch(cls -> goldKeys.contains(key(r, r, cls)))).toList();
        var unmodelled = runRules.valueStream()
                .filter(r -> "unmodelled_restriction".equals(r.path("kind").asString())).toList();

        var out = new ArrayList<String>();
        out.add("# Score: " + run.path("label").asString() + " (" + run.path("drafter").asString() + ")");
        out.add("");
        out.add("- Scope: %d prospectus pages %s".formatted(run.path("selected_pages").size(), run.path("selected_pages")));
        out.add("- Cost $%s, latency %ss (%s)".formatted(run.path("total_usd").asString(), run.path("total_seconds").asString(),
                run.path("usage").valueStream().map(u -> u.path("model").asString() + " " + u.path("seconds").asString() + "s $"
                        + u.path("usd").asString()).collect(Collectors.joining(", "))));
        out.add("- Card status: " + run.path("card").path("status").asString());
        out.add("- **Rule recall %d/%d**, **value accuracy %d/%d**".formatted(found, required, correct, required));
        out.add("- Citation support: %d/%d quotes verified on their page; %d rules with no citation".formatted(verified,
                allCites.size(), uncited));
        out.add("- Traps: " + String.join("; ", traps));
        out.add("");
        out.add("| gold rule | class | found | value | quotes verified | on gold page | confidence |");
        out.add("|---|---|---|---|---|---|---|");
        out.addAll(rows);
        if (!unexpected.isEmpty()) {
            out.add("");
            out.add("## Unexpected rules (review by hand)");
            unexpected.forEach(r -> out.add("- %s %s: %s".formatted(r.path("share_class").asString(), r.path("kind").asString(),
                    r.path("summary").asString())));
        }
        if (!unmodelled.isEmpty()) {
            out.add("");
            out.add("## Unmodelled Restrictions extracted");
            unmodelled.forEach(r -> out.add("- %s: %s".formatted(r.path("share_class").asString(), r.path("summary").asString())));
        }
        var report = String.join("\n", out);
        Files.writeString(Path.of(runFile.toString().replaceFirst("\\.json$", ".md")), report + "\n");
        return report;
    }

    /** Every gold quote is on its cited page, and automatic page selection covers the gold prospectus pages. */
    public void checkGold() throws IOException {
        var docs = new LinkedHashMap<String, FundDocument>();
        int bad = 0;
        for (var r : gold().path("rules")) {
            for (var c : r.path("citations")) {
                var doc = docs.computeIfAbsent(c.path("doc").asString(), documents::load);
                boolean ok = QuoteCheck.norm(doc.page(c.path("page").asInt())).contains(QuoteCheck.norm(c.path("quote").asString()));
                bad += ok ? 0 : 1;
                var q = c.path("quote").asString();
                System.out.printf("%s %-18s %s p%d: %s%n", ok ? "ok " : "BAD", r.path("id").asString(), doc.id(),
                        c.path("page").asInt(), q.substring(0, Math.min(70, q.length())));
            }
        }
        var fund = props.fund();
        var auto = PageSelection.select(docs.computeIfAbsent(fund.prospectus(), documents::load), fund.name(), 40);
        var needed = new TreeSet<Integer>();
        for (var r : gold().path("rules")) {
            if (!flag(r, "optional")) {
                r.path("citations").forEach(c -> {
                    if (fund.prospectus().equals(c.path("doc").asString())) {
                        needed.add(c.path("page").asInt());
                    }
                });
            }
        }
        System.out.printf("%nquotes not on their page: %d%nautomatic scope (%d pages) %s covers gold prospectus pages %s: missing %s%n",
                bad, auto.size(), auto, needed, needed.stream().filter(p -> !auto.contains(p)).toList());
    }

    private JsonNode gold() throws IOException {
        return json.readTree(props.fund().gold().toFile());
    }

    private static List<String> expand(JsonNode rule, List<String> classes) {
        var cls = rule.path("share_class").asString();
        return "*".equals(cls) ? classes : List.of(cls);
    }

    private static Key key(JsonNode rule, JsonNode params, String cls) {
        var kind = rule.path("kind").asString();
        return new Key(cls, kind, "target_market".equals(kind) ? params.path("dimension").asString(null) : null);
    }

    private static boolean flag(JsonNode n, String name) {
        return n.path(name).asBoolean(false);
    }

    private static boolean valueOk(JsonNode gold, JsonNode got) {
        for (var e : gold.path("params").properties()) {
            var field = e.getKey();
            var want = e.getValue();
            if (field.equals("dimension")) {
                continue;
            }
            var acceptNode = gold.path("accept").path(field);
            List<JsonNode> accepted = acceptNode.isArray() ? acceptNode.valueStream().toList() : List.of(want);
            var have = got.get(field);
            switch (field) {
                case "allowed_categories" -> {
                    var h = sorted(have);
                    // accept lists alternative arrays; params holds a single array
                    var options = acceptNode.isArray() ? accepted : List.of(want);
                    if (options.stream().noneMatch(a -> sorted(a).equals(h))) {
                        return false;
                    }
                }
                case "value" -> {
                    var h = QuoteCheck.norm(have == null || have.isNull() ? "" : have.asString());
                    if (accepted.stream().noneMatch(a -> QuoteCheck.norm(a.asString()).equals(h))) {
                        return false;
                    }
                }
                case "currency" -> {
                    // accept may list null: "no minimum" has no currency
                    var h = have == null || have.isNull() ? null : have.asString().toUpperCase();
                    if (accepted.stream().noneMatch(a -> a.isNull() ? h == null : a.asString().equals(h))) {
                        return false;
                    }
                }
                default -> {
                    if (accepted.stream().noneMatch(a -> same(have, a))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static List<String> sorted(JsonNode arr) {
        return arr == null || !arr.isArray() ? List.of() : arr.valueStream().map(JsonNode::asString).sorted().toList();
    }

    private static boolean same(JsonNode have, JsonNode want) {
        boolean haveNull = have == null || have.isNull() || have.isMissingNode();
        boolean wantNull = want == null || want.isNull();
        if (haveNull || wantNull) {
            return haveNull && wantNull;
        }
        if (have.isNumber() && want.isNumber()) {
            return have.asDouble() == want.asDouble();
        }
        if (have.isValueNode() && want.isValueNode()) {
            return Objects.equals(have.asString(), want.asString()) && have.getNodeType() == want.getNodeType();
        }
        return have.equals(want);
    }
}
