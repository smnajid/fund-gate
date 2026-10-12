package fundgate.rulecard.adapter.out.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import fundgate.rulecard.domain.Citation;
import fundgate.rulecard.domain.Rule;
import fundgate.rulecard.domain.RuleKind;

/** Prompts, JSON schema and wire-to-domain mapping shared by every LLM drafter. */
public final class RuleCardPrompts {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RuleCardPrompts() {
    }

    public static String system() {
        return text("prompts/system.txt").strip();
    }

    public static String template(String name, Map<String, String> values) {
        var t = text("prompts/" + name + ".txt").replace("{outputRules}", text("prompts/output-rules.txt").strip());
        for (var e : values.entrySet()) {
            t = t.replace("{" + e.getKey() + "}", e.getValue());
        }
        return t.strip();
    }

    public static String classes(Map<String, String> shareClasses) {
        return shareClasses.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    /** Schema with {@code citation_ids} (two-pass, Citations API). */
    public static ObjectNode schemaWithCitationIds(Map<String, String> shareClasses) {
        var schema = (ObjectNode) JSON.readTree(text("schema/rule-card.schema.json"));
        var shareClass = (ArrayNode) item(schema).path("properties").path("share_class").path("enum");
        shareClasses.keySet().forEach(shareClass::add);
        shareClass.add("*");
        return schema;
    }

    /** Schema where the model writes its own {file, page, quote} citations (no Citations API). */
    public static ObjectNode schemaWithQuotes(Map<String, String> shareClasses, List<String> files) {
        var schema = schemaWithCitationIds(shareClasses);
        var rule = item(schema);
        ((ObjectNode) rule.path("properties")).remove("citation_ids");
        var required = (ArrayNode) rule.path("required");
        required.remove(required.size() - 1);
        required.add("citations");
        var citation = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        citation.putArray("required").add("file").add("page").add("quote");
        var props = citation.putObject("properties");
        var file = props.putObject("file").put("type", "string").putArray("enum");
        files.forEach(file::add);
        props.putObject("page").put("type", "integer");
        props.putObject("quote").put("type", "string");
        ((ObjectNode) rule.path("properties")).putObject("citations").put("type", "array").set("items", citation);
        return schema;
    }

    /** Maps the model's JSON rules to domain rules; {@code citations} turns each rule's raw node into citations. */
    public static List<Rule> rules(JsonNode root, Function<JsonNode, List<Citation>> citations) {
        var rules = new ArrayList<Rule>();
        for (var r : root.path("rules")) {
            rules.add(new Rule(
                    r.path("share_class").asString(),
                    Rule.Scope.valueOf(r.path("scope").asString().toUpperCase()),
                    RuleKind.valueOf(r.path("kind").asString().toUpperCase()),
                    r.path("allowed_categories").isArray()
                            ? r.path("allowed_categories").valueStream().map(JsonNode::asString).toList() : null,
                    str(r, "us_person_definition"),
                    r.path("excludes_us_citizens").isBoolean() ? r.path("excludes_us_citizens").asBoolean() : null,
                    r.path("amount").isNumber() ? r.path("amount").asDouble() : null,
                    str(r, "currency"),
                    r.path("mandate_only").isBoolean() ? r.path("mandate_only").asBoolean() : null,
                    r.path("kid_available").isBoolean() ? r.path("kid_available").asBoolean() : null,
                    str(r, "dimension"),
                    str(r, "value"),
                    r.path("contradiction").asBoolean(),
                    r.path("confidence").asDouble(),
                    r.path("summary").asString(),
                    citations.apply(r)));
        }
        return rules;
    }

    public static double usd(String model, Long input, Long output, Long cacheWrite, Long cacheRead) {
        // $ per MTok: input, output, cache write (5m), cache read
        double[] p = model.contains("opus") ? new double[] {4.0, 20.0, 5.0, 0.20} : new double[] {2.0, 10.0, 2.5, 0.20};
        return (n(input) * p[0] + n(output) * p[1] + n(cacheWrite) * p[2] + n(cacheRead) * p[3]) / 1e6;
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    private static String str(JsonNode r, String field) {
        return r.path(field).isString() ? r.path(field).asString() : null;
    }

    private static JsonNode item(ObjectNode schema) {
        return schema.path("properties").path("rules").path("items");
    }

    private static String text(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
