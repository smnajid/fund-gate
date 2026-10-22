package fundeli5.rulecard.adapter.out.llm.claudeplan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.adapter.out.llm.RuleCardPrompts;
import fundeli5.rulecard.application.port.out.RuleCardDrafter;
import fundeli5.rulecard.domain.Citation;

/**
 * PROTOTYPE drafter on the Claude plan: one headless {@code claude -p} session, Read tool only, in a temp dir
 * holding just the PDFs (it can't see the answer key). The model writes its own {file, page, quote}
 * citations instead of using the Citations API, so the quote check is what catches made-up quotes.
 * Cost is Claude Code's list-price estimate, not a bill.
 */
public class ClaudePlanDrafter implements RuleCardDrafter {

    private final String model;
    private final JsonMapper json = JsonMapper.builder().build();

    public ClaudePlanDrafter(String model) {
        this.model = model;
    }

    @Override
    public String name() {
        return "claude-plan " + model;
    }

    @Override
    public Draft draft(Request req) {
        var byFile = new LinkedHashMap<String, Pdf>();
        req.documents().forEach(pdf -> byFile.put(pdf.docId() + ".pdf", pdf));
        try {
            var work = Files.createTempDirectory("rule-card-");
            for (var e : byFile.entrySet()) {
                Files.write(work.resolve(e.getKey()), e.getValue().bytes());
            }
            var files = byFile.entrySet().stream()
                    .map(e -> "- " + e.getKey() + ": " + e.getValue().title()
                            + (e.getValue().originalPages().size() > 0 ? ", " + e.getValue().originalPages().size() + " pages" : ""))
                    .collect(Collectors.joining("\n"));
            var prompt = RuleCardPrompts.system() + "\n\n" + RuleCardPrompts.template("plan", java.util.Map.of(
                    "files", files, "fund", req.fund(), "classes", RuleCardPrompts.classes(req.shareClasses())));
            var schema = RuleCardPrompts.schemaWithQuotes(req.shareClasses(), List.copyOf(byFile.keySet()));

            var cmd = List.of("claude", "-p", prompt, "--model", model, "--tools", "Read", "--allowedTools", "Read",
                    "--output-format", "stream-json", "--verbose", "--json-schema", json.writeValueAsString(schema),
                    "--no-session-persistence");
            var pb = new ProcessBuilder(cmd).directory(work.toFile()).redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
            // An exported API key takes precedence over the claude.ai login and bills the API, not the plan.
            pb.environment().remove("ANTHROPIC_API_KEY");
            long t = System.nanoTime();
            var result = stream(pb.start(), t);
            double seconds = (System.nanoTime() - t) / 1e9;

            if (result == null) {
                throw new IllegalStateException("claude -p ended without a result event");
            }
            if (result.path("is_error").asBoolean()) {
                throw new IllegalStateException("claude -p failed: " + result.path("result").asString());
            }
            var data = result.has("structured_output") ? result.get("structured_output")
                    : json.readTree(result.path("result").asString());
            var rules = RuleCardPrompts.rules(data, r -> citations(r, byFile));

            var mu = result.path("modelUsage").path(model);
            var usage = new Usage(model + " (claude -p)", Math.round(seconds * 10) / 10.0,
                    num(mu, "inputTokens"), num(mu, "outputTokens"), num(mu, "cacheCreationInputTokens"),
                    num(mu, "cacheReadInputTokens"), result.path("total_cost_usd").asDouble());
            return new Draft(rules, List.of(usage));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Prints every tool call and non-assistant event as it happens, so a stalled session is visible. */
    private JsonNode stream(Process proc, long t) throws IOException {
        JsonNode result = null;
        try (var in = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                var at = String.format("  [%5.0fs] ", (System.nanoTime() - t) / 1e9);
                JsonNode event;
                try {
                    event = json.readTree(line);
                } catch (RuntimeException notJson) {
                    System.out.println(at + line.substring(0, Math.min(200, line.length())));
                    continue;
                }
                switch (event.path("type").asString()) {
                    case "result" -> result = event;
                    case "assistant" -> event.path("message").path("content").forEach(block -> {
                        if ("tool_use".equals(block.path("type").asString())) {
                            var input = block.path("input").toString();
                            System.out.println(at + block.path("name").asString() + " " + input.substring(0, Math.min(120, input.length())));
                        }
                    });
                    case "user", "stream_event" -> {
                    }
                    default -> System.out.println(at + event.path("type").asString() + " " + event.path("subtype").asString(""));
                }
            }
        }
        return result;
    }

    private static List<Citation> citations(JsonNode rule, LinkedHashMap<String, Pdf> byFile) {
        var out = new ArrayList<Citation>();
        for (var c : rule.path("citations")) {
            var pdf = byFile.get(c.path("file").asString());
            Integer page = pdf == null ? null : pdf.originalPage(c.path("page").asInt());
            out.add(new Citation(pdf == null ? c.path("file").asString() : pdf.docId(), page,
                    page == null ? List.of() : List.of(page), c.path("quote").asString(), null));
        }
        return out;
    }

    private static Long num(JsonNode n, String field) {
        return n.path(field).isNumber() ? n.path(field).asLong() : null;
    }
}
