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
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        } catch (RuntimeException e) {
            // An unreadable PDF or catalog stops this fund only; the run goes on with the next
            return new Drafted.Refused("The pipeline failed on " + anyShareClassOfTheFund.value() + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Drafted draftOrRefuse(Isin asked) {
        List<ShareClass> classes = classesOfTheSameFund(asked);
        String fundName = classes.getFirst().fundName();
        List<FundDocument> sources = documents.documents(asked);
        if (sources.isEmpty()) {
            throw new Refusal("There is no Fund Documentation for " + fundName);
        }
        String subFund = subFundName(fundName);
        List<DraftPrompt.Pages> read = sources.stream().map(document -> read(document, subFund)).toList();
        LlmResponse answer;
        try {
            answer = llm.complete(DraftPrompt.request(model, fundName, classes, read));
        } catch (RuntimeException e) {
            // The egress client's messages name the model and the step, never the prompt
            throw new Refusal("The model gave no answer for " + fundName + ": " + e.getMessage());
        }
        Instant now = clock.instant();
        Map<DocumentId, List<String>> pages = read.stream().collect(Collectors.toMap(
                document -> document.document().documentId(), DraftPrompt.Pages::text, (first, second) -> {
                    throw new Refusal("Two documents of " + fundName + " have the same id");
                }));
        List<Rule> rules = new ArrayList<>();
        List<String> notRead = new ArrayList<>();
        JsonNode drafted = drafted(answer);
        Map<String, Optional<Isin>> named = classesByName(classes);
        for (int index = 0; index < drafted.size(); index++) {
            try {
                rules.add(rule(drafted.get(index), index + 1, named, pages, answer.model(), now));
            } catch (Refusal refusal) {
                notRead.add(refusal.getMessage());
            }
        }
        return new Drafted.Draft(new RuleCard(
                classes.getFirst().fundId(),
                fundName,
                classes.stream().map(ShareClass::isin).toList(),
                1,
                RuleCard.Status.DRAFT,
                rules,
                sources.stream().map(RuleCardPipeline::source).toList(),
                new RuleCard.Drafting(answer.model(), endpoint, now),
                Optional.empty()), notRead);
    }

    /**
     * The name a prospectus gives the sub-fund's own section: an umbrella prospectus names "Global Climate and
     * Environment Fund", seldom "Nordea 1 – Global Climate and Environment Fund". The spike selected on it.
     */
    static String subFundName(String fundName) {
        int dash = fundName.lastIndexOf(" – ");
        return dash < 0 ? fundName : fundName.substring(dash + 3);
    }

    /**
     * Each class by its name and by its ISIN, as the prompt lists them, e.g. "BP-EUR (LU0348926287)". A name two
     * classes share maps to none: a rule naming it is not read rather than put on a class by guess.
     */
    private static Map<String, Optional<Isin>> classesByName(List<ShareClass> classes) {
        Map<String, Optional<Isin>> named = new HashMap<>();
        for (ShareClass shareClass : classes) {
            Optional<Isin> isin = Optional.of(shareClass.isin());
            named.merge(shareClass.shareClassName(), isin, (first, second) -> Optional.empty());
            named.put(shareClass.isin().value(), isin);
            named.put(shareClass.shareClassName() + " (" + shareClass.isin().value() + ")", isin);
        }
        return named;
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

    private static JsonNode drafted(LlmResponse answer) {
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
        return drafted;
    }

    /** A rule that cannot be read is left off the card and listed for the validator, never guessed. */
    private static Rule rule(JsonNode drafted, int number, Map<String, Optional<Isin>> classes,
            Map<DocumentId, List<String>> pages, String model, Instant now) {
        String shareClass = drafted.path("share_class").asString("*").strip();
        Optional<Isin> isin = shareClass.equals("*") ? Optional.empty() : Optional.of(classes
                .getOrDefault(shareClass, Optional.empty())
                .orElseThrow(() -> new Refusal("Rule " + number + " names the Share Class " + shareClass
                        + ", which is not one class of the fund")));
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

    /** Read as a card's condition is, then as its kind proposes it, e.g. an Unmodelled Restriction Blocking (ADR 0003). */
    private static Condition condition(JsonNode drafted, int number) {
        if (!drafted.isObject()) {
            throw new Refusal("Rule " + number + " has no condition");
        }
        try {
            return RuleCatalog.asDrafted(JSON.treeToValue(drafted, Condition.class));
        } catch (JacksonException e) {
            throw new Refusal("Rule " + number + " has a condition outside the Rule Card schema (kind "
                    + drafted.path("kind").asString("missing") + ")");
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
