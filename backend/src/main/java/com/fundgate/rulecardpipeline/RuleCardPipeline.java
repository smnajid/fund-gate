package com.fundgate.rulecardpipeline;

import com.fundgate.bankdata.fixture.FixtureJson;
import com.fundgate.citation.Citation;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.DocumentUnavailable;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.LlmResponse;
import com.fundgate.rulecard.Condition;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecard.RuleKind;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.ruleengine.UnmodelledRestrictionKind;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drafts the Rule Card of one fund from its public Fund Documentation (ADR 0001, ADR 0004): checks each document is
 * the public file its metadata names, selects the pages that matter, has the model draft each rule with its quotes
 * through the egress client, and checks every quote on the page it cites. The card is always a Draft; reading no
 * Client Profile is structural, as it has no port to one.
 */
public class RuleCardPipeline {

    /** Pages sent per document: a whole KID, or a prospectus's selected pages. The spike's budget. */
    static final int MAX_PAGES = 40;

    // The model's conditions are read as a card's are: by the kind's name, refusing a field the kind does not have
    private static final JsonMapper JSON = FixtureJson.knowing(RuleCatalog.conditionTypes());

    private final FundDocuments documents;
    private final ShareClasses catalog;
    private final LlmEgress llm;
    private final String model;
    private final String endpoint;
    private final Clock clock;

    /** {@code endpoint} names the egress client's endpoint on the card, e.g. replay. */
    public RuleCardPipeline(FundDocuments documents, ShareClasses catalog, LlmEgress llm, String model,
            String endpoint, Clock clock) {
        this.documents = documents;
        this.catalog = catalog;
        this.llm = llm;
        this.model = model;
        this.endpoint = endpoint;
        this.clock = clock;
    }

    /** The Draft card of the fund the Share Class belongs to, or why there is none. Never throws for one fund. */
    public Drafted draft(Isin anyShareClassOfTheFund) {
        try {
            return draftOrRefuse(anyShareClassOfTheFund);
        } catch (Refusal refusal) {
            return new Drafted.Refused(refusal.getMessage());
        }
    }

    private Drafted draftOrRefuse(Isin asked) {
        List<ShareClass> classes = classesOfTheSameFund(asked);
        String fundName = classes.getFirst().fundName();
        List<FundDocument> sources = documents.documents(asked);
        if (sources.isEmpty()) {
            throw new Refusal("There is no Fund Documentation for " + fundName);
        }
        List<DraftPrompt.Pages> read = sources.stream().map(document -> read(document, fundName)).toList();
        LlmResponse answer;
        try {
            answer = llm.complete(DraftPrompt.request(model, fundName, classes, read));
        } catch (RuntimeException e) {
            // The egress client's messages name the model and the step, never the prompt
            throw new Refusal("The model gave no answer for " + fundName + ": " + e.getMessage());
        }
        Instant now = clock.instant();
        Map<DocumentId, List<String>> pages = read.stream()
                .collect(Collectors.toMap(document -> document.document().documentId(), DraftPrompt.Pages::text));
        List<Rule> rules = rules(answer, classes, pages, now);
        return new Drafted.Draft(new RuleCard(
                classes.getFirst().fundId(),
                fundName,
                classes.stream().map(ShareClass::isin).toList(),
                1,
                RuleCard.Status.DRAFT,
                rules,
                sources.stream().map(RuleCardPipeline::source).toList(),
                new RuleCard.Drafting(answer.model(), endpoint, now),
                Optional.empty()));
    }

    /** Same fundId when the catalog has one, else the same fund (sub-fund) name, as the documents adapter does. */
    private List<ShareClass> classesOfTheSameFund(Isin isin) {
        List<ShareClass> all = catalog.all();
        ShareClass asked = all.stream().filter(shareClass -> shareClass.isin().equals(isin)).findFirst()
                .orElseThrow(() -> new Refusal("No Share Class " + isin.value() + " in the catalog"));
        return all.stream()
                .filter(other -> asked.fundId().isPresent()
                        ? asked.fundId().equals(other.fundId())
                        : Objects.equals(asked.fundName(), other.fundName()))
                .toList();
    }

    /** Only the public file the metadata names is read: a hash that does not match is another file. */
    private DraftPrompt.Pages read(FundDocument document, String fundName) {
        String id = document.documentId().value();
        if (document.contentSha256().isBlank()) {
            throw new Refusal("The document " + id + " has no content hash, so its file cannot be checked");
        }
        if (!document.confidentiality().equals(Optional.of(FundDocument.Confidentiality.PUBLIC))) {
            throw new Refusal("The document " + id + " is not public: the pipeline drafts from public "
                    + "Fund Documentation only");
        }
        byte[] pdf;
        try {
            pdf = documents.content(document.documentId());
        } catch (DocumentUnavailable e) {
            throw new Refusal(e.getMessage());
        }
        if (!sha256(pdf).equalsIgnoreCase(document.contentSha256())) {
            throw new Refusal("The file of " + id + " does not match its content hash");
        }
        List<String> text;
        try {
            text = PdfPages.read(pdf);
        } catch (IOException e) {
            throw new Refusal("Cannot read the PDF of " + id + ": " + e.getMessage());
        }
        return new DraftPrompt.Pages(document, text, PageSelection.select(text, fundName, MAX_PAGES));
    }

    private static List<Rule> rules(LlmResponse answer, List<ShareClass> classes, Map<DocumentId, List<String>> pages,
            Instant now) {
        JsonNode drafted;
        try {
            drafted = JSON.readTree(withoutFence(answer.text())).path("rules");
        } catch (JacksonException e) {
            // The answer stays out of the reason: Jackson's message would quote it
            throw new Refusal("The model's answer is not JSON");
        }
        if (!drafted.isArray()) {
            throw new Refusal("The model's answer has no list of rules");
        }
        Map<String, Isin> byName = classes.stream()
                .collect(Collectors.toMap(ShareClass::shareClassName, ShareClass::isin, (first, second) -> first));
        List<Rule> rules = new ArrayList<>();
        for (int index = 0; index < drafted.size(); index++) {
            rules.add(rule(drafted.get(index), index + 1, byName, pages, answer.model(), now));
        }
        return rules;
    }

    private static Rule rule(JsonNode drafted, int number, Map<String, Isin> classes,
            Map<DocumentId, List<String>> pages, String model, Instant now) {
        String shareClass = drafted.path("share_class").asString("*");
        Optional<Isin> isin = shareClass.equals("*") ? Optional.empty() : Optional.of(Optional
                .ofNullable(classes.get(shareClass))
                .orElseThrow(() -> new Refusal("Rule " + number + " names the Share Class " + shareClass
                        + ", which the fund does not have")));
        Condition condition = condition(drafted.path("condition"), number);
        List<Citation.Document> citations = new ArrayList<>();
        for (JsonNode citation : drafted.path("citations")) {
            citations.add(checked(citation, pages));
        }
        return new Rule(
                isin.isEmpty() ? Rule.Scope.FUND : Rule.Scope.SHARE_CLASS,
                isin,
                condition,
                citations,
                drafted.path("summary").asString(""),
                drafted.path("confidence").asDouble(0.0),
                drafted.path("contradiction").asBoolean(false),
                List.of(new Rule.Revision(Rule.Revision.Action.DRAFTED, "Rule Card pipeline (" + model + ")", now,
                        condition)));
    }

    /** ADR 0003: every Unmodelled Restriction is proposed Blocking, whatever the model says; only a validator notes one. */
    private static Condition condition(JsonNode drafted, int number) {
        if (!(drafted instanceof ObjectNode condition)) {
            throw new Refusal("Rule " + number + " has no condition");
        }
        ObjectNode copy = condition.deepCopy();
        if (copy.path("kind").asString("").equals(RuleKind.UNMODELLED_RESTRICTION.name().toLowerCase(Locale.ROOT))) {
            copy.remove("notedReason");
            copy.put("status", UnmodelledRestrictionKind.UnmodelledRestriction.Status.BLOCKING.name());
        }
        try {
            return JSON.treeToValue(copy, Condition.class);
        } catch (JacksonException e) {
            throw new Refusal("Rule " + number + " has a condition outside the Rule Card schema (kind "
                    + copy.path("kind").asString("missing") + ")");
        }
    }

    /** A quote not found on its page, or citing a document that was not sent, stays on the card flagged. */
    private static Citation.Document checked(JsonNode citation, Map<DocumentId, List<String>> pages) {
        DocumentId document = new DocumentId(citation.path("document").asString(""));
        int page = citation.path("page").asInt(0);
        String quote = citation.path("quote").asString("");
        boolean found = QuoteCheck.onPage(quote, pages.getOrDefault(document, List.of()), page);
        return new Citation.Document(document, page, quote, Optional.of(found));
    }

    /** Models often wrap JSON in a Markdown fence. */
    private static String withoutFence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int body = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return body < 0 || end <= body ? trimmed : trimmed.substring(body + 1, end);
    }

    private static RuleCard.Source source(FundDocument document) {
        return new RuleCard.Source(document.documentId(), document.contentSha256(), document.fmdEditionDate(),
                document.pdfEditionDate(), FundDocument.Confidentiality.PUBLIC);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Why one fund gets no card this run; caught in {@link #draft}. */
    private static final class Refusal extends RuntimeException {

        Refusal(String reason) {
            super(reason, null, false, false);
        }
    }
}
