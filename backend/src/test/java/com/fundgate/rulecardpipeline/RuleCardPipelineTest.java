package com.fundgate.rulecardpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundgate.bankdata.InvestorType;
import com.fundgate.citation.Citation;
import com.fundgate.funddocuments.DocumentId;
import com.fundgate.funddocuments.DocumentUnavailable;
import com.fundgate.funddocuments.FundDocument;
import com.fundgate.funddocuments.FundDocuments;
import com.fundgate.llm.LlmEgress;
import com.fundgate.llm.LlmRequest;
import com.fundgate.llm.LlmResponse;
import com.fundgate.rulecard.Rule;
import com.fundgate.rulecard.RuleCard;
import com.fundgate.rulecardpipeline.Drafted.Draft;
import com.fundgate.ruleengine.RuleCatalog;
import com.fundgate.ruleengine.InvestorCategoryKind.InvestorCategory;
import com.fundgate.ruleengine.MinimumInitialSubscriptionKind.MinimumInitialSubscription;
import com.fundgate.ruleengine.TargetMarketKind.TargetMarket;
import com.fundgate.ruleengine.UnmodelledRestrictionKind.UnmodelledRestriction;
import com.fundgate.ruleengine.UsPersonExclusionKind.UsPersonExclusion;
import com.fundgate.rulecardpipeline.Drafted.Refused;
import com.fundgate.shareclasses.Isin;
import com.fundgate.shareclasses.ShareClass;
import com.fundgate.shareclasses.ShareClasses;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class RuleCardPipelineTest {

    private static final Isin BP = new Isin("LU0348926287");
    private static final Isin BI = new Isin("LU0348927095");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private static final byte[] PROSPECTUS = pdf(
            "Unless otherwise decided by the board, US persons may not subscribe for shares in the SICAV.",
            "I Institutional investors. P All investors. The board may reject any application.");
    private static final byte[] KID_BP = pdf("We have classified this Fund as 4 out of 7.");

    private static final String ANSWER = """
            ```json
            {"rules": [
              {"share_class": "*", "condition": {"kind": "us_person_exclusion", "definition": "reg_s",
               "excludesUsCitizens": false}, "contradiction": false, "confidence": 0.9,
               "summary": "US persons may not subscribe.",
               "citations": [{"document": "prospectus", "page": 1, "quote": "US persons may not subscribe for shares"}]},
              {"share_class": "BI-EUR", "condition": {"kind": "investor_category", "allowed": ["institutional"],
               "excluded": []}, "contradiction": false, "confidence": 0.8, "summary": "Institutional investors only.",
               "citations": [{"document": "prospectus", "page": 2, "quote": "I Institutional investors."}]},
              {"share_class": "BI-EUR", "condition": {"kind": "minimum_initial_subscription", "amount": 75000,
               "currency": "EUR"}, "contradiction": false, "confidence": 0.7, "summary": "EUR 75,000.",
               "citations": [{"document": "prospectus", "page": 1, "quote": "Minimum initial investment EUR 75,000"}]},
              {"share_class": "*", "condition": {"kind": "unmodelled_restriction", "status": "noted",
               "notedReason": "harmless"}, "contradiction": false, "confidence": 0.6,
               "summary": "The board may reject any application.",
               "citations": [{"document": "prospectus", "page": 2, "quote": "The board may reject any application."}]},
              {"share_class": "BP-EUR", "condition": {"kind": "target_market", "dimension": "risk_sri",
               "polarity": "positive", "value": "4"}, "contradiction": false, "confidence": 0.9, "summary": "SRI 4.",
               "citations": [{"document": "kid-bp", "page": 1, "quote": "classified this Fund as 4 out of 7"}]}
            ]}
            ```""";

    private final List<LlmRequest> sent = new ArrayList<>();

    @Test
    void drafts_a_draft_card_whose_quotes_are_each_checked_on_their_page() {
        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI);

        RuleCard card = draft.card();
        assertThat(card.status()).isEqualTo(RuleCard.Status.DRAFT);
        assertThat(card.version()).isEqualTo(1);
        assertThat(card.validation()).isEmpty();
        assertThat(card.fundName()).isEqualTo("Nordea 1 – Global Climate and Environment Fund");
        assertThat(card.shareClasses()).containsExactlyInAnyOrder(BP, BI);
        assertThat(card.drafting()).isEqualTo(new RuleCard.Drafting("model-from-the-egress", "replay", NOW));
        assertThat(card.sources()).extracting(RuleCard.Source::documentId)
                .containsExactly(new DocumentId("prospectus"), new DocumentId("kid-bp"));
        assertThat(card.rules()).extracting(rule -> (Object) rule.condition().getClass()).containsExactly(
                UsPersonExclusion.class, InvestorCategory.class, MinimumInitialSubscription.class,
                UnmodelledRestriction.class, TargetMarket.class);
        assertThat(card.rules()).flatExtracting(Rule::citations).extracting(Citation.Document::quoteFoundOnPage)
                .containsExactly(Optional.of(true), Optional.of(true), Optional.of(false), Optional.of(true),
                        Optional.of(true));
    }

    @Test
    void a_quote_not_found_on_its_page_is_flagged_not_dropped() {
        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI);

        assertThat(draft.rulesNotRead()).isEmpty();
        assertThat(draft.quotesNotFound()).singleElement().satisfies(citation -> {
            assertThat(citation.quote()).isEqualTo("Minimum initial investment EUR 75,000");
            assertThat(citation.quoteFoundOnPage()).contains(false);
        });
        assertThat(draft.card().rules()).anySatisfy(rule ->
                assertThat(rule.condition()).isEqualTo(new MinimumInitialSubscription(new BigDecimal("75000"), "EUR")));
    }

    @Test
    void maps_each_rule_to_its_scope_share_class_and_typed_condition() {
        RuleCard card = ((Draft) pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI)).card();

        Rule usPerson = card.rules().get(0);
        assertThat(usPerson.scope()).isEqualTo(Rule.Scope.FUND);
        assertThat(usPerson.shareClass()).isEmpty();
        assertThat(usPerson.condition()).isEqualTo(new UsPersonExclusion(
                UsPersonExclusion.UsPersonDefinition.REG_S, false));
        Rule investors = card.rules().get(1);
        assertThat(investors.scope()).isEqualTo(Rule.Scope.SHARE_CLASS);
        assertThat(investors.shareClass()).contains(BI);
        assertThat(investors.condition()).isEqualTo(new InvestorCategory(Set.of(InvestorType.INSTITUTIONAL), Set.of()));
        assertThat(card.rules().get(4).condition()).isEqualTo(new TargetMarket(
                TargetMarket.Dimension.RISK_SRI, TargetMarket.Polarity.POSITIVE, "4"));
        assertThat(card.rules()).allSatisfy(rule -> assertThat(rule.history()).singleElement().satisfies(revision -> {
            assertThat(revision.action()).isEqualTo(Rule.Revision.Action.DRAFTED);
            assertThat(revision.at()).isEqualTo(NOW);
            assertThat(revision.condition()).isEqualTo(rule.condition());
        }));
    }

    @Test
    void proposes_every_unmodelled_restriction_as_blocking_even_when_the_model_notes_it() {
        RuleCard card = ((Draft) pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI)).card();

        assertThat(card.rules()).filteredOn(rule -> rule.condition() instanceof UnmodelledRestriction)
                .singleElement()
                .satisfies(rule -> assertThat(rule.condition()).isEqualTo(new UnmodelledRestriction(
                        UnmodelledRestriction.Status.BLOCKING, Optional.empty())));
    }

    @Test
    void sends_the_model_each_page_labelled_with_its_document_and_page_number() {
        pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI);

        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.model()).isEqualTo("claude-test");
            assertThat(request.prompt())
                    .contains("Nordea 1 – Global Climate and Environment Fund")
                    .contains("BP-EUR", "BI-EUR")
                    .contains("<page document=\"prospectus\" number=\"2\">")
                    .contains("I Institutional investors.")
                    .contains("<page document=\"kid-bp\" number=\"1\">");
        });
    }

    @Test
    void refuses_a_document_without_a_content_hash_and_calls_no_llm() {
        FundDocument noHash = document("prospectus", PROSPECTUS, "", Optional.of(FundDocument.Confidentiality.PUBLIC));

        Drafted drafted = pipeline(documents(Map.of(noHash, PROSPECTUS)), answering(ANSWER)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("prospectus").contains("no content hash"));
        assertThat(sent).isEmpty();
    }

    @Test
    void refuses_a_document_whose_bytes_do_not_match_its_hash() {
        FundDocument wrongFile = document("prospectus", KID_BP, sha256(PROSPECTUS), Optional.of(FundDocument.Confidentiality.PUBLIC));

        Drafted drafted = pipeline(documents(Map.of(wrongFile, KID_BP)), answering(ANSWER)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("does not match its content hash"));
        assertThat(sent).isEmpty();
    }

    @Test
    void refuses_a_document_not_marked_public() {
        FundDocument confidential = document("prospectus", PROSPECTUS, sha256(PROSPECTUS), Optional.empty());

        Drafted drafted = pipeline(documents(Map.of(confidential, PROSPECTUS)), answering(ANSWER)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("not public"));
        assertThat(sent).isEmpty();
    }

    @Test
    void drafts_nothing_for_a_fund_without_documents() {
        Drafted drafted = pipeline(documents(Map.of()), answering(ANSWER)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("no Fund Documentation"));
    }

    @Test
    void drafts_nothing_when_a_pdf_cannot_be_read() {
        FundDocuments unreadable = new FundDocuments() {
            public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
                return List.of(prospectus());
            }

            public byte[] content(DocumentId id) {
                throw new DocumentUnavailable("Cannot read the PDF of " + id, null);
            }
        };

        Drafted drafted = pipeline(unreadable, answering(ANSWER)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("Cannot read the PDF of prospectus"));
    }

    @Test
    void drafts_nothing_when_the_llm_egress_client_gives_no_answer() {
        LlmEgress down = request -> {
            sent.add(request);
            throw new IllegalStateException("The model cannot be reached");
        };

        Drafted drafted = pipeline(documents(prospectus(), kidBp()), down).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("The model cannot be reached"));
    }

    @Test
    void a_rule_naming_a_share_class_the_fund_does_not_have_is_listed_not_read() {
        String answer = """
                {"rules": [{"share_class": "Z-USD", "condition": {"kind": "kid_required_for_retail",
                  "kidAvailable": true}, "contradiction": false, "confidence": 0.9, "summary": "KID.",
                  "citations": [{"document": "kid-bp", "page": 1, "quote": "classified this Fund"}]}]}""";

        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(answer)).draft(BI);

        assertThat(draft.card().rules()).isEmpty();
        assertThat(draft.rulesNotRead()).singleElement().asString().contains("Rule 1", "Z-USD");
    }

    @Test
    void a_rule_whose_condition_has_a_field_its_kind_does_not_have_is_listed_not_read() {
        String answer = """
                {"rules": [{"share_class": "*", "condition": {"kind": "kid_required_for_retail",
                  "kid_available": true}, "contradiction": false, "confidence": 0.9, "summary": "KID.",
                  "citations": [{"document": "kid-bp", "page": 1, "quote": "classified this Fund"}]}]}""";

        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(answer)).draft(BI);

        assertThat(draft.card().rules()).isEmpty();
        assertThat(draft.rulesNotRead()).singleElement().asString()
                .contains("Rule 1", "kid_required_for_retail").doesNotContain("kid_available");
    }

    @Test
    void a_rule_may_name_its_share_class_by_isin_or_as_the_prompt_lists_it() {
        String answer = """
                {"rules": [
                  {"share_class": "LU0348927095", "condition": {"kind": "kid_required_for_retail",
                   "kidAvailable": true}, "summary": "KID.", "citations": []},
                  {"share_class": "BP-EUR (LU0348926287)", "condition": {"kind": "kid_required_for_retail",
                   "kidAvailable": true}, "summary": "KID.", "citations": []}]}""";

        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(answer)).draft(BI);

        assertThat(draft.card().rules()).extracting(Rule::shareClass).containsExactly(Optional.of(BI), Optional.of(BP));
        assertThat(draft.rulesNotRead()).isEmpty();
    }

    @Test
    void any_failure_on_one_fund_refuses_that_fund_rather_than_throwing() {
        ShareClasses down = () -> {
            throw new IllegalStateException("The catalog cannot be read");
        };

        Drafted drafted = new RuleCardPipeline(documents(prospectus()), down, answering(ANSWER), "claude-test",
                "replay", Clock.fixed(NOW, ZoneOffset.UTC)).draft(BI);

        assertThat(drafted).isInstanceOfSatisfying(Refused.class,
                refused -> assertThat(refused.reason()).contains("The catalog cannot be read"));
    }

    @Test
    void a_quote_citing_a_document_that_was_not_sent_is_flagged() {
        String answer = """
                {"rules": [{"share_class": "*", "condition": {"kind": "kid_required_for_retail",
                  "kidAvailable": true}, "contradiction": false, "confidence": 0.9, "summary": "KID.",
                  "citations": [{"document": "kid-elsewhere", "page": 1, "quote": "classified this Fund"}]}]}""";


        Draft draft = (Draft) pipeline(documents(prospectus(), kidBp()), answering(answer)).draft(BI);

        assertThat(draft.quotesNotFound()).singleElement()
                .satisfies(citation -> assertThat(citation.document()).isEqualTo(new DocumentId("kid-elsewhere")));
    }

    @Test
    void selects_pages_on_the_sub_fund_s_own_name() {
        assertThat(RuleCardPipeline.subFundName("Nordea 1 – Global Climate and Environment Fund"))
                .isEqualTo("Global Climate and Environment Fund");
        assertThat(RuleCardPipeline.subFundName("BNP PARIBAS (CH) Bonds CHF")).isEqualTo("BNP PARIBAS (CH) Bonds CHF");
    }

    @Test
    void asks_for_every_kind_of_the_rule_catalog() {
        pipeline(documents(prospectus(), kidBp()), answering(ANSWER)).draft(BI);

        assertThat(sent.getFirst().system()).contains(RuleCatalog.draftingPrompt());
    }

    @Test
    void drafts_nothing_when_the_answer_is_not_a_rule_card() {
        Drafted drafted = pipeline(documents(prospectus(), kidBp()), answering("I cannot help with that.")).draft(BI);

        assertThat(drafted).isInstanceOf(Refused.class);
    }

    // --- fixtures

    private RuleCardPipeline pipeline(FundDocuments documents, LlmEgress llm) {
        return new RuleCardPipeline(documents, catalog(), llm, "claude-test", "replay",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LlmEgress answering(String text) {
        return request -> {
            sent.add(request);
            return new LlmResponse("model-from-the-egress", text);
        };
    }

    private static ShareClasses catalog() {
        return () -> List.of(
                shareClass(BP, "BP-EUR"),
                shareClass(BI, "BI-EUR"),
                new ShareClass(new Isin("LU2607415275"), Optional.empty(), "Nordea SIF – Global Private Equity Fund",
                        Optional.empty(), "BW-SEK", "SEK", Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), NOW));
    }

    private static ShareClass shareClass(Isin isin, String name) {
        return new ShareClass(isin, Optional.empty(), "Nordea 1 – Global Climate and Environment Fund",
                Optional.empty(), name, "EUR", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), NOW);
    }

    private static FundDocument prospectus() {
        return document("prospectus", PROSPECTUS, sha256(PROSPECTUS), Optional.of(FundDocument.Confidentiality.PUBLIC));
    }

    private static FundDocument kidBp() {
        return new FundDocument(new DocumentId("kid-bp"), List.of(BP), Optional.of(FundDocument.Type.KID),
                Optional.of("en"), Optional.empty(), Optional.empty(), Optional.of(FundDocument.Confidentiality.PUBLIC),
                sha256(KID_BP), Optional.empty(), Optional.of("kid-bp.pdf"));
    }

    private static FundDocument document(String id, byte[] pdf, String hash,
            Optional<FundDocument.Confidentiality> confidentiality) {
        return new FundDocument(new DocumentId(id), List.of(BP, BI), Optional.of(FundDocument.Type.PROSPECTUS),
                Optional.of("en"), Optional.empty(), Optional.of("2026-06"), confidentiality, hash, Optional.empty(),
                Optional.of(id + ".pdf"));
    }

    private static FundDocuments documents(FundDocument... documents) {
        Map<FundDocument, byte[]> contents = new java.util.LinkedHashMap<>();
        for (FundDocument document : documents) {
            contents.put(document, document.documentId().value().equals("kid-bp") ? KID_BP : PROSPECTUS);
        }
        return documents(contents);
    }

    private static FundDocuments documents(Map<FundDocument, byte[]> contents) {
        return new FundDocuments() {
            public List<FundDocument> documents(Isin anyShareClassOfTheFund) {
                return List.copyOf(contents.keySet());
            }

            public byte[] content(DocumentId id) {
                return contents.entrySet().stream().filter(entry -> entry.getKey().documentId().equals(id))
                        .map(Map.Entry::getValue).findFirst().orElseThrow();
            }
        };
    }

    private static byte[] pdf(String... pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    content.newLineAtOffset(40, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
