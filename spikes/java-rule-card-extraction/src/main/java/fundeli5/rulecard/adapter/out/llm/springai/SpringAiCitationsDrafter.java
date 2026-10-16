package fundeli5.rulecard.adapter.out.llm.springai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.CitationsConfigParam;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

import tools.jackson.databind.json.JsonMapper;

import fundeli5.rulecard.adapter.out.llm.RuleCardPrompts;
import fundeli5.rulecard.application.port.out.RuleCardDrafter;
import fundeli5.rulecard.domain.Citation;

/**
 * Two-pass drafter on the Anthropic API (or Microsoft Foundry via ANTHROPIC_BASE_URL), Spring AI owning the
 * connection.
 * <ol>
 * <li>Pass 1, cited extraction: PDFs with Citations on. Goes through the SDK client under Spring AI
 * ({@link AnthropicChatModel#getAnthropicClient()}) because Spring AI's ChatModel joins all text blocks and
 * returns citations as one flat list, losing which passage supports which statement.</li>
 * <li>Pass 2, structured output: Spring AI ChatModel with {@code outputSchema}; rules point at pass-1 [cN]
 * citation ids.</li>
 * </ol>
 */
public class SpringAiCitationsDrafter implements RuleCardDrafter {

    private final AnthropicChatModel chatModel;
    private final String extractModel;
    private final String normaliseModel;
    private final JsonMapper json = JsonMapper.builder().build();

    public SpringAiCitationsDrafter(AnthropicChatModel chatModel, String extractModel, String normaliseModel) {
        this.chatModel = chatModel;
        this.extractModel = extractModel;
        this.normaliseModel = normaliseModel;
    }

    @Override
    public String name() {
        return "spring-ai " + extractModel + " + " + normaliseModel;
    }

    @Override
    public Draft draft(Request req) {
        long t = System.nanoTime();
        var message = chatModel.getAnthropicClient().messages().create(pass1(req));
        var pass1Usage = usage(extractModel, message, (System.nanoTime() - t) / 1e9);
        var table = new LinkedHashMap<String, Citation>();
        var notes = serialise(message, req.documents(), table);
        System.out.printf("pass 1: %d cited passages, %.0fs%n", table.size(), pass1Usage.seconds());

        t = System.nanoTime();
        var schema = RuleCardPrompts.schemaWithCitationIds(req.shareClasses());
        var options = AnthropicChatOptions.builder()
                .model(normaliseModel)
                .maxTokens(16000)
                .outputSchema(json.writeValueAsString(schema))
                .build();
        var response = chatModel.call(new Prompt(RuleCardPrompts.template("pass2", Map.of("notes", notes)), options));
        double seconds = (System.nanoTime() - t) / 1e9;
        var u = response.getMetadata().getUsage();
        long in = u.getPromptTokens() == null ? 0 : u.getPromptTokens();
        long out = u.getCompletionTokens() == null ? 0 : u.getCompletionTokens();
        var pass2Usage = new Usage(normaliseModel, round(seconds), in, out, null, null,
                RuleCardPrompts.usd(normaliseModel, in, out, null, null));

        var rules = RuleCardPrompts.rules(cardJson(response), r -> {
            var cites = new ArrayList<Citation>();
            r.path("citation_ids").forEach(id -> cites.add(table.getOrDefault(id.asString(),
                    new Citation(null, null, List.of(), null, false))));
            return cites;
        });
        return new Draft(rules, List.of(pass1Usage, pass2Usage));
    }

    /**
     * The structured-output JSON. Not just {@code getResult()}: with thinking on, the response can hold several
     * generations, and the first one may not be the JSON. Fails loudly, keeping the raw text, rather than returning
     * an empty card.
     */
    private tools.jackson.databind.JsonNode cardJson(org.springframework.ai.chat.model.ChatResponse response) {
        var texts = new ArrayList<String>();
        for (var g : response.getResults()) {
            var text = g.getOutput().getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            texts.add(text);
            var start = text.indexOf('{');
            if (start < 0) {
                continue;
            }
            try {
                var node = json.readTree(text.substring(start));
                if (node.path("rules").isArray()) {
                    return node;
                }
            } catch (RuntimeException e) {
                // not this one
            }
        }
        try {
            var dump = java.nio.file.Files.writeString(java.nio.file.Path.of("runs", "pass2-unparsed.txt"),
                    response.getResults().size() + " generations\n\n" + String.join("\n\n----\n\n", texts));
            throw new IllegalStateException("pass 2 returned no Rule Card JSON; raw text in " + dump);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("pass 2 returned no Rule Card JSON", e);
        }
    }

    private MessageCreateParams pass1(Request req) {
        var blocks = new ArrayList<ContentBlockParam>();
        var docs = req.documents();
        for (int i = 0; i < docs.size(); i++) {
            var pdf = docs.get(i);
            var doc = DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(Base64.getEncoder().encodeToString(pdf.bytes())).build())
                    .title(pdf.title())
                    .citations(CitationsConfigParam.builder().enabled(true).build());
            if (i == docs.size() - 1) {
                doc.cacheControl(CacheControlEphemeral.builder().build());
            }
            blocks.add(ContentBlockParam.ofDocument(doc.build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(RuleCardPrompts.template("pass1",
                Map.of("fund", req.fund(), "classes", RuleCardPrompts.classes(req.shareClasses())))).build()));
        return MessageCreateParams.builder()
                .model(extractModel)
                .maxTokens(32000)
                .system(RuleCardPrompts.system())
                .addUserMessageOfBlockParams(blocks)
                .build();
    }

    /** Cited text blocks become prose with [cN] markers, plus a citation table mapped to original pages. */
    private static String serialise(Message message, List<Pdf> docs, Map<String, Citation> table) {
        var out = new StringBuilder();
        for (var block : message.content()) {
            block.text().ifPresent(text -> {
                out.append(text.text());
                text.citations().ifPresent(cs -> cs.forEach(c -> c.pageLocation().ifPresent(p -> {
                    var cid = "c" + (table.size() + 1);
                    var pdf = docs.get((int) p.documentIndex());
                    // end_page_number is exclusive; taking it inclusively only adds a page to search
                    var pages = new ArrayList<Integer>();
                    for (long k = p.startPageNumber(); k <= Math.min(p.endPageNumber(), pdf.originalPages().size()); k++) {
                        pages.add(pdf.originalPage((int) k));
                    }
                    table.put(cid, new Citation(pdf.docId(), pages.isEmpty() ? null : pages.getFirst(), pages,
                            p.citedText(), null));
                    out.append(" [").append(cid).append(']');
                })));
            });
        }
        return out.toString();
    }

    private static Usage usage(String model, Message m, double seconds) {
        var u = m.usage();
        Long cw = u.cacheCreationInputTokens().orElse(null);
        Long cr = u.cacheReadInputTokens().orElse(null);
        return new Usage(model, round(seconds), u.inputTokens(), u.outputTokens(), cw, cr,
                RuleCardPrompts.usd(model, u.inputTokens(), u.outputTokens(), cw, cr));
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
